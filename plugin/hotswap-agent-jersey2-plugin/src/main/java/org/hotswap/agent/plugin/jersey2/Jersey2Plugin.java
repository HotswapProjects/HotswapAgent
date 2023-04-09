/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.jersey2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "Jersey2",
        description = "Jersey2 framework plugin - this does not handle HK2 changes",
        testedVersions = {"2.10.1"},
        expectedVersions = {"2.10.1"})
public class Jersey2Plugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(Jersey2Plugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredJerseyContainers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
//    Set<Object> registeredServiceLocators = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    Set<Class<?>> allRegisteredClasses = Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());

    /**
     *  Initialize the plugin when Jersey's ServletContainer.init(WebConfig config) is called.  This is called from both init() for a servlet
     *  and init(Config) for a filter.
     *
     *  Also, add the ServletContainer to a list of registeredJerseyContainers so that we can call reload on it later when classes change
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.servlet.ServletContainer")
    public static void jerseyServletCallInitialized(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init", new CtClass[] { classPool.get("org.glassfish.jersey.servlet.WebConfig") });
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(Jersey2Plugin.class));
        LOGGER.info("org.glassfish.jersey.servlet.ServletContainer enhanced with plugin initialization.");

        String registerThis = PluginManagerInvoker.buildCallPluginMethod(Jersey2Plugin.class, "registerJerseyContainer", "this",
                "java.lang.Object", "getConfiguration()", "java.lang.Object"/*, "getApplicationHandler().getServiceLocator()", "java.lang.Object"*/);
        init.insertAfter(registerThis);

        // Workaround a Jersey issue where ServletContainer cannot be reloaded since it is in an immutable state
        CtMethod reload = ctClass.getDeclaredMethod("reload", new CtClass[] { classPool.get("org.glassfish.jersey.server.ResourceConfig") });
        reload.insertBefore("$1 = new org.glassfish.jersey.server.ResourceConfig($1);");
    }

    /**
     *  Fix a scanning issue with jersey pre-2.4 versions.  https://java.net/jira/browse/JERSEY-1936
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.server.internal.scanning.AnnotationAcceptingListener")
    public static void fixAnnoationAcceptingListener(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod process = ctClass.getDeclaredMethod("process");
        process.insertAfter("try { $2.close(); } catch (Exception e) {}");
    }

    /**
     * Fix CDI CDI_MULTIPLE_LOCATORS_INTO_SIMPLE_APP exception on class redefinition
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.ext.cdi1x.internal.SingleHk2LocatorManager")
    public static void fixSingleHk2LocatorManager(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod process = ctClass.getDeclaredMethod("registerLocator");
        process.insertBefore("if (this.locator != null) return;");
        LOGGER.debug("SingleHk2LocatorManager : patched()");
    }

    /**
     * Register the jersey container and the classes involved in configuring the Jersey Application
     */
    public void registerJerseyContainer(Object jerseyContainer, Object resourceConfig/*, Object serviceLocator*/) {
        try {
            Class<?> resourceConfigClass = resolveClass("org.glassfish.jersey.server.ResourceConfig");

            LOGGER.info("Jersey2Plugin - registerJerseyContainer : " + jerseyContainer.getClass().getName());

            Set<Class<?>> containerClasses = getContainerClasses(resourceConfigClass, resourceConfig);

            registeredJerseyContainers.add(jerseyContainer);
            allRegisteredClasses.addAll(containerClasses);
            /*
            if (serviceLocator != null) {
                registeredServiceLocators.add(serviceLocator);
            }
            */

            LOGGER.debug("registerJerseyContainer : finished");
        } catch (Exception e) {
            LOGGER.error("Error registering Jersey Container.", e);
        }
    }

    /**
     * Gets a list of classes used in configure the Jersey Application
     */
    private Set<Class<?>> getContainerClasses(Class<?> resourceConfigClass, Object resourceConfig)
                throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        Method scanClassesMethod = resourceConfigClass.getDeclaredMethod("scanClasses");
        scanClassesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> scannedClasses = (Set<Class<?>>) scanClassesMethod.invoke(resourceConfig);

        Method getRegisteredClassesMethod = resourceConfigClass.getDeclaredMethod("getRegisteredClasses");
        getRegisteredClassesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> registeredClasses = (Set<Class<?>>)getRegisteredClassesMethod.invoke(resourceConfig);

        Set<Class<?>> containerClasses = Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());
        containerClasses.addAll(scannedClasses);
        containerClasses.addAll(registeredClasses);
        return containerClasses;
    }

    /**
     * Call reload on the jersey Application when any class changes that is either involved in configuring
     * the Jersey Application, or if was newly annotated and will be involved in configuring the application.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidate(CtClass ctClass, Class original) throws Exception {
        boolean reloaded = false;
        if (allRegisteredClasses.contains(original)) {
            scheduler.scheduleCommand(reloadJerseyContainers);
            reloaded = true;
        } else {
            // TODO: When a class is not annotated at startup, and is annotated during debug, it never gets found
            // here.  Is this a DCEVM issue?  Also, the Jersey Container  does not find the newly annotated class
            // during a reload called from reloadJerseyContainers, so this seems like the annotation is not being
            // added
            if (AnnotationHelper.hasAnnotation(original, "javax.ws.rs.Path")
                    || AnnotationHelper.hasAnnotation(ctClass, "javax.ws.rs.Path")) {
                allRegisteredClasses.add(original);
                scheduler.scheduleCommand(reloadJerseyContainers);
                reloaded = true;
            }

        }
        if (!reloaded) {
            // reload if HK2 Service class is changed
            if (AnnotationHelper.hasAnnotation(original, "org.jvnet.hk2.annotations.Service")
                    || AnnotationHelper.hasAnnotation(ctClass, "org.jvnet.hk2.annotations.Service")) {

                scheduler.scheduleCommand(reloadJerseyContainers);
                // TODO : reload SystemDescriptor in case of Service change?
                // scheduler.scheduleCommand(disposeReflectionCaches);
            }
        }
    }

    /**
     * Call reload on the Jersey Application
     */
    private Command reloadJerseyContainers = new Command() {
        public void executeCommand() {
            try {
                Class<?> containerClass = resolveClass("org.glassfish.jersey.server.spi.Container");
                Method reloadMethod = containerClass.getDeclaredMethod("reload");

                for (Object jerseyContainer : registeredJerseyContainers) {
                    reloadMethod.invoke(jerseyContainer);
                }
                LOGGER.info("Reloaded Jersey Containers");
            } catch (Exception e) {
                LOGGER.error("Error reloading Jersey Container.", e);
            }
        }
    };

    /**
     * Dispose service locators reflection caches
     */
    /*
    private Command disposeReflectionCaches = new Command() {
        public void executeCommand() {
            if (!registeredServiceLocators.isEmpty()) {
                try {
                    LOGGER.debug("Disposing reflection caches");
                    for (Object serviceLocator : registeredServiceLocators) {
                        ReflectionHelper.invoke(serviceLocator, serviceLocator.getClass(), "clearReflectionCache", new Class[]{});
                    }
                    LOGGER.info("Reflection caches disposed.");
                } catch (Exception e) {
                    LOGGER.error("executeCommand() exception {}.", e.getMessage());
                }
            }
        }
    };
    */

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}

