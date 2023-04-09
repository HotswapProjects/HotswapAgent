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
package org.hotswap.agent.plugin.vaadin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Vaadin 14.0+ plugin for HotswapAgent.
 *
 * https://vaadin.com
 *
 * @author Artur Signell
 * @author Matti Tahvonen
 * @author Johannes Eriksson
 */
@Plugin(name = "Vaadin",
        description = "Vaadin support",
        testedVersions = {"23.0.0", "24.0.0.beta1"},
        expectedVersions = {"23 - 24"})
public class VaadinPlugin {

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @Init
    PluginConfiguration pluginConfiguration;

    private UpdateRoutesCommand updateRouteRegistryCommand;

    private ReflectionCommand reloadCommand;

    private ReflectionCommand clearReflectionCache = new ReflectionCommand(this,
            "com.vaadin.flow.internal.ReflectionCache", "clearAll");

    private Set<Class<?>> addedClasses = new HashSet<>();

    private Set<Class<?>> modifiedClasses = new HashSet<>();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(VaadinPlugin.class);

    private static final String RELOAD_QUIET_TIME_PARAMETER = "vaadin.liveReloadQuietTime";

    private static final int DEFAULT_RELOAD_QUIET_TIME = 1000; // ms

    private int reloadQuietTime = 0;

    public VaadinPlugin() {
    }

    @OnClassLoadEvent(classNameRegexp = "com.vaadin.flow.server.VaadinServlet")
    public static void init(CtClass ctClass)
            throws NotFoundException, CannotCompileException {
        String src = PluginManagerInvoker
                .buildInitializePlugin(VaadinPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(VaadinPlugin.class,
                "registerServlet", "this", Object.class.getName());
        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(src);

        LOGGER.info("Initialized Vaadin plugin");
    }

    public void registerServlet(Object vaadinServlet) {
        try {
            Class<?> vaadinIntegrationClass = resolveClass("org.hotswap.agent.plugin.vaadin.VaadinIntegration");
            Object vaadinIntegration = vaadinIntegrationClass.getConstructor()
                    .newInstance();
            Class<?> vaadinServletClass = resolveClass("com.vaadin.flow.server.VaadinServlet");
            Method m = vaadinIntegrationClass.getDeclaredMethod("servletInitialized",
                    vaadinServletClass);
            m.invoke(vaadinIntegration, vaadinServlet);

            updateRouteRegistryCommand = new UpdateRoutesCommand(vaadinIntegration);
            reloadCommand = new ReflectionCommand(vaadinIntegration, "reload");
        } catch (ClassNotFoundException | NoSuchMethodException
                | InstantiationException | IllegalAccessException
                | InvocationTargetException ex) {
            LOGGER.error(null, ex);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateReflectionCache(CtClass ctClass) throws Exception {
        LOGGER.debug("Redefined class {}, clearing Vaadin reflection cache and reloading browser", ctClass.getName());
        scheduler.scheduleCommand(clearReflectionCache);
        scheduler.scheduleCommand(reloadCommand, getReloadQuietTime());
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = { FileEvent.CREATE, FileEvent.MODIFY })
    public void classCreated(FileEvent eventType, CtClass ctClass) throws Exception {
        if (FileEvent.CREATE.equals(eventType)) {
            LOGGER.debug("Create class file event for " + ctClass.getName());
            addedClasses.add(resolveClass(ctClass.getName()));
        } else if (FileEvent.MODIFY.equals(eventType)) {
            LOGGER.debug("Modify class file event for " + ctClass.getName());
            modifiedClasses.add(resolveClass(ctClass.getName()));
        }
        // Note that scheduling multiple calls to the same command postpones it
        scheduler.scheduleCommand(updateRouteRegistryCommand);
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

    private int getReloadQuietTime() {
        if (reloadQuietTime <= 0) {
            reloadQuietTime = DEFAULT_RELOAD_QUIET_TIME;
            String reloadQuietTimeValue = pluginConfiguration.getProperty(RELOAD_QUIET_TIME_PARAMETER);
            if (reloadQuietTimeValue != null) {
                if (reloadQuietTimeValue.matches("[1-9][0-1]+")) {
                    reloadQuietTime = Integer.parseInt(reloadQuietTimeValue);
                    LOGGER.info("Live-reload quiet time is {} ms", reloadQuietTime);
                } else {
                    LOGGER.error("Illegal value '{}' for parameter {}, using default of {} ms",
                            reloadQuietTimeValue, RELOAD_QUIET_TIME_PARAMETER, DEFAULT_RELOAD_QUIET_TIME);
                }
            }
        }
        return reloadQuietTime;
    }

    private class UpdateRoutesCommand extends ReflectionCommand {
        private final Object vaadinIntegration;

        UpdateRoutesCommand(Object vaadinIntegration) {
            super(vaadinIntegration, "updateRoutes", addedClasses, modifiedClasses);
            this.vaadinIntegration = vaadinIntegration;
        }

        // NOTE: Identity equality semantics

        @Override
        public boolean equals(Object that) {
            return this == that;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(vaadinIntegration);
        }

        @Override
        public void executeCommand() {
            super.executeCommand();
            addedClasses.clear();
            modifiedClasses.clear();
        }
    }
}
