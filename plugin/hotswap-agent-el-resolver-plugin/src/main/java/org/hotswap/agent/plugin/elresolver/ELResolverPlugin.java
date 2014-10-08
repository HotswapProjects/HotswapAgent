package org.hotswap.agent.plugin.elresolver;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Clear javax.el.BeanELResolver cache after a class is redefined.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "ELResolver",
        description = "Purge BeanELResolver class cache on any class change.",
        testedVersions = {"2.2"},
        expectedVersions = {"2.2"})
public class ELResolverPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ELResolverPlugin.class);

    public static final String PURGE_CLASS_CACHE_METHOD_NAME = "__purgeClassCache";

    @Init
    Scheduler scheduler;

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure this plugin is initialized
     * - register the instance using registerBeanELResolver() method
     */
    @OnClassLoadEvent(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {

        String initPlugin = PluginManagerInvoker.buildInitializePlugin(ELResolverPlugin.class);
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(ELResolverPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(initPlugin);
            constructor.insertAfter(registerThis);
        }

        try {
            // JUEL, JSF BeanELResolver[s]
            // check if we have purgeBeanClasses method
            CtMethod purgeMeth = ctClass.getDeclaredMethod("purgeBeanClasses");
//            purgeMeth.setModifiers(org.hotswap.agent.javassist.Modifier.PUBLIC);
            ctClass.addMethod(CtNewMethod.make("public void " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader) {" +
                    "   java.beans.Introspector.flushCaches(); " +
                    "   purgeBeanClasses(classLoader); " +
                    "}", ctClass));
        } catch (NotFoundException e) {
            try {
                // Apache (Tomcat's) BeanELResolver
                ctClass.addMethod(CtNewMethod.make("public void " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader) {" +
                        "   java.beans.Introspector.flushCaches(); " +
                        "   this.cache = new javax.el.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                        "}", ctClass));
            } catch (Exception e1) {
                LOGGER.warning("Unable to add javax.el.BeanELResolver." + PURGE_CLASS_CACHE_METHOD_NAME + "() method. Purging will not be available.", e);
            }
        }

        LOGGER.debug("javax.el.BeanELResolver - added method " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader).");
    }

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
        LOGGER.debug("ELResolverPlugin - BeanELResolver registered : " + beanELResolver.getClass().getName());
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache(ClassLoader appClassLoader) throws Exception {
        PurgeBeanELResolverCacheCommand cmd = new PurgeBeanELResolverCacheCommand(appClassLoader, registeredBeanELResolvers);
        scheduler.scheduleCommand(cmd);
    }


}
