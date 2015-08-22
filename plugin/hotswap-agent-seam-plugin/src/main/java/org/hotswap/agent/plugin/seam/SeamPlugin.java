package org.hotswap.agent.plugin.seam;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Plugin(name = "Seam",
        description = "Seam framework (http://seamframework.org/). Clears java.beans.Introspector cache and org.jboss.el.util.ReferenceCache on any class redefinition.",
        testedVersions = {"2.3.1"},
        expectedVersions = {"2.2", "2.3"})
public class SeamPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SeamPlugin.class);

    ReflectionCommand flushBeanIntrospectors = new ReflectionCommand(this, "java.beans.Introspector", "flushCaches");

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredJbossReferenceCaches = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @OnClassLoadEvent(classNameRegexp = "org.jboss.seam.init.Initialization")
    public static void seamServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(SeamPlugin.class));
        LOGGER.debug("org.jboss.seam.init.Initialization enhanced with plugin initialization.");
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void flushBeanIntrospectorsCaches() throws Exception {
        scheduler.scheduleCommand(flushBeanIntrospectors);
    }

    public void registerJbossReferenceCache(Object referenceCache) {
        registeredJbossReferenceCaches.add(referenceCache);
        LOGGER.debug("SeamPlugin - registerJbossReferenceCache : " + referenceCache.getClass().getName());
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.util.ReferenceCache")
    public static void referenceCacheRegisterVariable(CtClass ctClass) throws CannotCompileException {
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(SeamPlugin.class, "registerJbossReferenceCache",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache() throws Exception {
        scheduler.scheduleCommand(clearJbossReferenceCache);
    }

    private Command clearJbossReferenceCache = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing Jboss reference caches.");
            try {
                Method clearCacheMethod = resolveClass("org.jboss.el.util.ReferenceCache").getDeclaredMethod("clear");
                for (Object referenceCache : registeredJbossReferenceCaches) {
                    clearCacheMethod.invoke(referenceCache);
                }
            } catch (Exception e) {
                LOGGER.error("Error clear in jboss ReferenceCache .", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
