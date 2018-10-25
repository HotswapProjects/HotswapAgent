package org.hotswap.agent.plugin.weld.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
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
     * Add custom tracker field to session context
     *
     * @param ctClass the class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.AbstractContext")
    public static void transformHttpSessionContext(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        HaCdiCommons.transformContext(classPool, ctClass);
    }
}
