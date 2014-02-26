package org.hotswap.agent.plugin.jsf;

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

@Plugin(name = "JSF",
        description = "JSF Framework maintains Label cache and bean resolver cache.",
        testedVersions = {"2.1.23"},
        expectedVersions = {"2.1", "2.2"})
public class JsfPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JsfPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @Transform(classNameRegexp = "org.apache.catalina.loader.WebappLoader")
    public static void elCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod startInternalMethod = ctClass.getDeclaredMethod("startInternal");
        startInternalMethod.insertAfter(PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class));
        LOGGER.debug("org.apache.catalina.loader.WebappLoader enahnced with plugin initialization.");
    }

    @Transform(classNameRegexp = "com.sun.faces.config.ConfigManager")
    public static void facesServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("initialize");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class));
        LOGGER.debug("javax.faces.webapp.FacesServlet enahnced with plugin initialization.");
    }

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
        LOGGER.debug("JsfPlugin - BeanELResolver registred : " + beanELResolver.getClass().getName());
    }

    @Transform(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(JsfPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this.cache = new javax.el.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                "}", ctClass));

        LOGGER.debug("javax.el.BeanELResolver - added method __resetCache().");
    }

    @Transform(classNameRegexp = ".*", onDefine = false)
    public void invalidateClassCache() throws Exception {
        scheduler.scheduleCommand(invalidateClassCache);
    }

    private Command invalidateClassCache = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing JSF BeanELResolver caches.");
            try {
                Method beanElResolverMethod = resolveClass("javax.el.BeanELResolver").getDeclaredMethod("__resetCache");
                for (Object registeredBeanELResolver : registeredBeanELResolvers) {
                    beanElResolverMethod.invoke(registeredBeanELResolver);
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
