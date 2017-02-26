package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;
import org.hotswap.agent.plugin.weld.beans.WeldHotswapContext;

/**
 * The Class CdiContextsTransformer.
 *
 * @author alpapad@gmail.com
 */
public class CdiContextsTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CdiContextsTransformer.class);

    /**
     * Add context reloading functionality to base contexts classes
     *
     * @param clazz the clazz
     * @param classPool the class pool
     * @param cl the class loader
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
    public static void transformReloadingWeldContexts(CtClass clazz, ClassPool classPool, ClassLoader cl) throws NotFoundException, CannotCompileException {

        LOGGER.debug("Adding interface {} to {}.", WeldHotswapContext.class.getName(), clazz.getName());
        clazz.addInterface(classPool.get(WeldHotswapContext.class.getName()));

        CtField toReloadFld = CtField.make("public transient java.util.Set __toReloadWeld = null;", clazz);
        clazz.addField(toReloadFld);

        CtField reloadingFld = CtField.make("public transient boolean __reloadingWeld = false;", clazz);
        clazz.addField(reloadingFld);

        CtMethod addBeanToReload = CtMethod.make(
                "public void __addBeanToReloadWeld(javax.enterprise.context.spi.Contextual bean) {" +
                "    if (__toReloadWeld == null)" +
                "        __toReloadWeld = new java.util.HashSet();" +
                "    __toReloadWeld.add(bean);" +
                "}",
                clazz
        );
        clazz.addMethod(addBeanToReload);

        CtMethod getBeansToReload = CtMethod.make("public java.util.Set __getBeansToReloadWeld(){return __toReloadWeld;}", clazz);
        clazz.addMethod(getBeansToReload);

        CtMethod reload = CtMethod.make("public void __reloadWeld() {" + ContextualReloadHelper.class.getName() +".reload(this);}", clazz);
        clazz.addMethod(reload);

        CtMethod isActiveCopy = CtMethod.make("public boolean __isActiveWeld(){return false;}", clazz);
        isActiveCopy.setBody(clazz.getDeclaredMethod("isActive"), null);
        clazz.addMethod(isActiveCopy);

        CtMethod isActive = clazz.getDeclaredMethod("isActive");
        isActive.setBody(
                "{  " +
                "    boolean active = __isActiveWeld(); " +
                "    if(active && !__reloadingWeld ) { " +
                "        __reloadingWeld = true;" +
                "        __reloadWeld();" +
                "        __reloadingWeld = false;" +
                "    }" +
                "    return active;" +
                "}"
        );

        //addDestroyMethod(clazz, classPool);

        LOGGER.debug("Class '{}' patched with hot-swapping support", clazz.getName() );
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.beanstore.http.LazySessionBeanStore")
    public static void transformLazySessionBeanStore(CtClass clazz, ClassPool classPool, ClassLoader cl) throws NotFoundException, CannotCompileException {

        CtMethod getSessionCopy = CtMethod.make("public javax.servlet.http.HttpSession __getSession(boolean create) {return null;}", clazz);
        getSessionCopy.setBody(clazz.getDeclaredMethod("getSession"), null);
        clazz.addMethod(getSessionCopy);

        CtMethod getSession = clazz.getDeclaredMethod("getSession");
        getSession.setBody(
                "{  " +
                "    boolean sessionExists = $1 && (getSessionIfExists()!=null); " +
                "    javax.servlet.http.HttpSession session = __getSession($1);" +
                "    if(!sessionExists && session!=null) {" +
                "        org.hotswap.agent.plugin.weld.command.HttpSessionsRegistry.addSeenSession(session);" +
                "    }" +
                "    return session;" +
                "}"
        );
    }
}
