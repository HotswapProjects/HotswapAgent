package org.hotswap.agent.plugin.elresolver;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Clear javax.el.BeanELResolver cache after a class is redefined.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "ELResolver",
        description = "Maintains ELResolver bean resolver cache.",
        testedVersions = {""},
        expectedVersions = {"all"})
public class ELResolverPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ELResolverPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    private Command invalidateClassCache = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing BeanELResolver caches.");
            try {
                Method beanElResolverMethod = resolveClass("javax.el.BeanELResolver").getDeclaredMethod("purgeBeanClasses", ClassLoader.class);
                for (Object registeredBeanELResolver : registeredBeanELResolvers) {
                    beanElResolverMethod.invoke(registeredBeanELResolver, appClassLoader);
                }
            } catch (Exception e) {
                LOGGER.error("Error refreshing Jsf BeanELResolver .", e);
            }
        }
    };

    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure this plugin is initialized
     * - register the instance using registerBeanELResolver() method
     */
    @Transform(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {

        String initPlugin = PluginManagerInvoker.buildInitializePlugin(ELResolverPlugin.class);
        String registerThis = PluginManagerInvoker.buildCallPluginMethod("org.hotswap.agent.plugin.elresolver.ELResolverPlugin.class.getClassLoader()", ELResolverPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(initPlugin);
            constructor.insertAfter(registerThis);
        }

        try {
            // JUEL, JSF BeanELResolver[s]
            CtMethod purgeMeth = ctClass.getDeclaredMethod("purgeBeanClasses");
            purgeMeth.setModifiers(org.hotswap.agent.javassist.Modifier.PUBLIC);
        } catch (NotFoundException e) {
            // Apache (Tomcat's) BeanELResolver
            ctClass.addMethod(CtNewMethod.make("public void purgeBeanClasses(java.lang.ClassLoader classLoader) {" +
                    "   this.cache = new javax.el.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                    "}", ctClass));
        }

        LOGGER.debug("javax.el.BeanELResolver - added method __resetCache().");
    }

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
        LOGGER.debug("ELResolverPlugin - BeanELResolver registred : " + beanELResolver.getClass().getName());
    }

    @Transform(classNameRegexp = ".*", onDefine = false)
    public void invalidateClassCache() throws Exception {
        scheduler.scheduleCommand(invalidateClassCache);
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
