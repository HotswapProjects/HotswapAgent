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
package org.hotswap.agent.plugin.jersey1;

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
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "Jersey1",
        description = "Jersey1 framework plugin - this does not handle HK2 changes",
        testedVersions = {"1.18.3"},
        expectedVersions = {"1.x"})
public class Jersey1Plugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(Jersey1Plugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredJerseyContainers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    Set<Class<?>> allRegisteredClasses = Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());

    /**
     *  Initialize the plugin when Jersey's ServletContainer.init(WebConfig config) is called.  This is called from both init() for a servlet
     *  and init(Config) for a filter.
     *
     *  Also, add the ServletContainer to a list of registeredJerseyContainers so that we can call reload on it later when classes change
     */
    @OnClassLoadEvent(classNameRegexp = "com.sun.jersey.spi.container.servlet.ServletContainer")
    public static void jerseyServletCallInitialized(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init", new CtClass[] { classPool.get("com.sun.jersey.spi.container.servlet.WebConfig") });
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(Jersey1Plugin.class));
        LOGGER.info("com.sun.jersey.spi.container.servlet.WebConfig enhanced with plugin initialization.");

        String registerThis = PluginManagerInvoker.buildCallPluginMethod(Jersey1Plugin.class, "registerJerseyContainer", "this",
                "java.lang.Object", "this.webComponent.getResourceConfig()", "java.lang.Object");
        init.insertAfter(registerThis);
    }

    /**
     * Register the jersey container and the classes involved in configuring the Jersey Application
     */
    public void registerJerseyContainer(Object jerseyContainer, Object resourceConfig) {
        try {
            Class<?> resourceConfigClass = resolveClass("com.sun.jersey.api.core.ResourceConfig");

            LOGGER.info("Jersey1 plugin - registerJerseyContainer : " + jerseyContainer.getClass().getName());

            Set<Class<?>> containerClasses = getContainerClasses(resourceConfigClass, resourceConfig);

            registeredJerseyContainers.add(jerseyContainer);
            allRegisteredClasses.addAll(containerClasses);

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

        Set<Class<?>> containerClasses = Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());

        Set<Class<?>> providerClasses = (Set<Class<?>>) ReflectionHelper.invoke(resourceConfig, resourceConfigClass, "getProviderClasses",  new Class[]{});
        if (providerClasses != null) {
            containerClasses.addAll(providerClasses);
        }

        Set<Class<?>> rootResourceClasses = (Set<Class<?>>) ReflectionHelper.invoke(resourceConfig, resourceConfigClass, "getRootResourceClasses",  new Class[]{});
        if (rootResourceClasses != null) {
            containerClasses.addAll(rootResourceClasses);
        }

        return containerClasses;
    }

    /**
     * Call reload on the jersey Application when any class changes that is either involved in configuring
     * the Jersey Application, or if was newly annotated and will be involved in configuring the application.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidate(CtClass ctClass, Class original) throws Exception {
        if (allRegisteredClasses.contains(original)) {
            scheduler.scheduleCommand(reloadJerseyContainers);
        } else {
            // TODO: When a class is not annotated at startup, and is annotated during debug, it never gets found
            // here.  Is this a DCEVM issue?  Also, the Jersey Container  does not find the newly annotated class
            // during a reload called from reloadJerseyContainers, so this seems like the annotation is not being
            // added
            // vd: it is wrong here, since original class is scanned for Path !
            if (AnnotationHelper.hasAnnotation(original, "javax.ws.rs.Path")
                    || AnnotationHelper.hasAnnotation(ctClass, "javax.ws.rs.Path")) {
                allRegisteredClasses.add(original);
                scheduler.scheduleCommand(reloadJerseyContainers);
            }
        }
    }

    /**
     * Call reload on the Jersey Application
     */
    private Command reloadJerseyContainers = new Command() {
        public void executeCommand() {
            try {
                Class<?> containerClass = resolveClass("com.sun.jersey.spi.container.servlet.ServletContainer");
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

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}

