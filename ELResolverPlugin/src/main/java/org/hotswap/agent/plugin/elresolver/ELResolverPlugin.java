package org.hotswap.agent.plugin.elresolver;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "ELResolver",
        description = "Maintains ELResolver bean resolver cache.",
        testedVersions = {""},
        expectedVersions = {""})
public class ELResolverPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ELResolverPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @Transform(classNameRegexp = "org.apache.catalina.loader.WebappLoader")
    public static void elCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod startInternalMethod = ctClass.getDeclaredMethod("startInternal");
        startInternalMethod.insertAfter(PluginManagerInvoker.buildInitializePlugin(ELResolverPlugin.class));
        LOGGER.debug("org.apache.catalina.loader.WebappLoader enahanced with plugin initialization.");
    }

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
        LOGGER.debug("ELResolverPlugin - BeanELResolver registred : " + beanELResolver.getClass().getName());
    }

    @Transform(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {

        String registerThis = PluginManagerInvoker.buildCallPluginMethod("org.hotswap.agent.plugin.elresolver.ELResolverPlugin.class.getClassLoader()", ELResolverPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }

        try {
            // JUEL, JSF BeanELResolver[s]
            CtMethod purgeMeth = ctClass.getDeclaredMethod("purgeBeanClasses");
            purgeMeth.setModifiers(org.hotswap.agent.javassist.Modifier.PUBLIC);
        }
        catch (NotFoundException e) {
            // Apache (Tomcat's) BeanELResolver
            ctClass.addMethod(CtNewMethod.make("public void purgeBeanClasses(java.lang.ClassLoader classLoader) {" +
                    "   this.cache = new javax.el.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                    "}", ctClass));
        }

        LOGGER.debug("javax.el.BeanELResolver - added method __resetCache().");
    }

    @Transform(classNameRegexp = ".*", onDefine = false)
    public void invalidateClassCache() throws Exception {
        scheduler.scheduleCommand(invalidateClassCache);
    }

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

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
