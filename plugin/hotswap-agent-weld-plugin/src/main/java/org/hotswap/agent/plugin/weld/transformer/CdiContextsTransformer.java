package org.hotswap.agent.plugin.weld.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;
import org.hotswap.agent.plugin.weld.beans.WeldHotswapContext;

/**
 * The Class CdiContextsTransformer.
 *
 * @author alpapad@gmail.com
 * @author Vladimir Dvorak
 */
public class CdiContextsTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CdiContextsTransformer.class);

    public static final String BOUND_SESSION_BEAN_STORE_REGISTRY = "$$ha$boundSessionBeanStoreRegistry";

    /**
     * Add context reloading functionality to base contexts classes.
     *
     * @param ctClass the class
     * @param classPool the class pool
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "(org.jboss.weld.context.AbstractManagedContext)|" +
                                        "(org.jboss.weld.context.AbstractSharedContext)|" +
                                        "(org.jboss.weld.context.unbound.DependentContextImpl)|" +
                                        "(org.jboss.weld.util.ForwardingContext)|" +
                                        "(org.apache.myfaces.flow.cdi.FlowScopedContextImpl)|" +
                                        "(org.apache.myfaces.cdi.view.ViewScopeContextImpl)"
                                        )
    public static void transformReloadingWeldContexts(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        LOGGER.debug("Adding interface {} to {}.", WeldHotswapContext.class.getName(), ctClass.getName());
        ctClass.addInterface(classPool.get(WeldHotswapContext.class.getName()));

        CtField toReloadFld = CtField.make("public transient java.util.Set $$ha$toReloadWeld = null;", ctClass);
        ctClass.addField(toReloadFld);

        CtField reloadingFld = CtField.make("public transient boolean $$ha$reloadingWeld = false;", ctClass);
        ctClass.addField(reloadingFld);

        CtMethod addBeanToReload = CtMethod.make(
                "public void $$ha$addBeanToReloadWeld(javax.enterprise.context.spi.Contextual bean) {" +
                    "if ($$ha$toReloadWeld == null)" +
                        "$$ha$toReloadWeld = new java.util.HashSet();" +
                    "$$ha$toReloadWeld.add(bean);" +
                "}",
                ctClass
        );
        ctClass.addMethod(addBeanToReload);

        CtMethod getBeansToReload = CtMethod.make("public java.util.Set $$ha$getBeansToReloadWeld(){return $$ha$toReloadWeld;}", ctClass);
        ctClass.addMethod(getBeansToReload);

        CtMethod reload = CtMethod.make("public void $$ha$reloadWeld() {" + ContextualReloadHelper.class.getName() +".reload(this);}", ctClass);
        ctClass.addMethod(reload);

        CtMethod isActive = ctClass.getDeclaredMethod("isActive");
        isActive.insertAfter(
                "{" +
                    "if($_ && !$$ha$reloadingWeld ) { " +
                        "$$ha$reloadingWeld = true;" +
                        "$$ha$reloadWeld();" +
                        "$$ha$reloadingWeld = false;" +
                    "}" +
                    "return $_;" +
                "}"
        );

        LOGGER.debug("Class '{}' patched with hot-swapping support", ctClass.getName() );
    }

    /**
     * Transform lazy session bean store.
     *
     * @param ctClass the class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.beanstore.http.LazySessionBeanStore")
    public static void transformLazySessionBeanStore(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtMethod getSession = ctClass.getDeclaredMethod("getSession");
        getSession.insertAfter(
            "if($_!=null) {" +
                "org.hotswap.agent.plugin.weld.command.HttpSessionsRegistry.addSession($_);" +
            "}" +
            "return $_;"
        );
    }

    /**
     * Add custom tracker field to session context
     *
     * @param ctClass the class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.http.HttpSessionContextImpl")
    public static void transformHttpSessionContext(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtField trackerFld = CtField.make("public java.util.Map " + HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD + "=new java.util.HashMap();", ctClass);
        ctClass.addField(trackerFld);

        LOGGER.debug("Custom context tracker field added to '{}'.", ctClass.getName() );
    }

    /**
     * Add custom tracker field to bound session context.
     *
     * @param ctClass the class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.bound.BoundSessionContextImpl")
    public static void transformBoundSessionContext(CtClass ctClass) throws NotFoundException, CannotCompileException {

        // Add custom contexts tracker for extensions contexts
        CtField customTrackerFld = CtField.make("public java.util.Map " + HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD + "=new java.util.HashMap();", ctClass);
        ctClass.addField(customTrackerFld);

        // Add bean store registry
        CtField trackerFld =
                CtField.make("public org.hotswap.agent.plugin.weld.command.BoundSessionBeanStoreRegistry " + BOUND_SESSION_BEAN_STORE_REGISTRY + ";", ctClass);
        ctClass.addField(trackerFld);
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(
                "this." + BOUND_SESSION_BEAN_STORE_REGISTRY + "=new org.hotswap.agent.plugin.weld.command.BoundSessionBeanStoreRegistry();"
            );
        }

        CtMethod getSession = ctClass.getDeclaredMethod("associate");
        getSession.insertAfter(
            "if($_) {" +
                BOUND_SESSION_BEAN_STORE_REGISTRY + ".addBeanStore($1);" +
            "}" +
            "return $_;"
        );

        CtMethod cleanupMethod = CtMethod.make(
                "public void cleanup() {" +
                    BOUND_SESSION_BEAN_STORE_REGISTRY + ".removeBeanStore(getBeanStore());" +
                    "super.cleanup();" +
                "}",
                ctClass
        );

        ctClass.addMethod(cleanupMethod);

        LOGGER.debug("Custom context tracker field added to '{}'.", ctClass.getName() );
    }

    /**
     * Add ha-delegate to passivation context
     *
     * @param ctClass the class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.PassivatingContextWrapper\\$AbstractPassivatingContextWrapper")
    public static void transformAbstractPassivatingContextWrapper(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtMethod delegateMethod = CtMethod.make(
                "public java.lang.Object " + HaCdiCommons.HA_DELEGATE + "() {" +
                    "return this.context;" +
                "}",
                ctClass
        );

        ctClass.addMethod(delegateMethod);

        LOGGER.debug(HaCdiCommons.HA_DELEGATE + " added to '{}'.", ctClass.getName() );
    }
}
