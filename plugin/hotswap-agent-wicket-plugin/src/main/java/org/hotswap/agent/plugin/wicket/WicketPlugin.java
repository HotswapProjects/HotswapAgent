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
package org.hotswap.agent.plugin.wicket;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Method;

/**
 * Wicket hotswap support
 *
 * https://wicket.apache.org
 *
 * @author Thomas Heigl
 */
@Plugin(name = "Wicket", description = "Wicket support", testedVersions = "8.0.0", expectedVersions = "8.x")
public class WicketPlugin {

    private static final String WICKET_APPLICATION = "org.apache.wicket.protocol.http.WebApplication";

    private static final AgentLogger LOGGER = AgentLogger.getLogger(WicketPlugin.class);

    @Init
    Scheduler scheduler;
    @Init
    ClassLoader appClassLoader;

    private Object wicketApplication;

    @OnClassLoadEvent(classNameRegexp = WICKET_APPLICATION)
    public static void init(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String src = PluginManagerInvoker
                .buildInitializePlugin(WicketPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(WicketPlugin.class,
                "registerApplication", "this", "java.lang.Object");
        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(src);

        LOGGER.info("Wicket application has been enhanced.");
    }

    public void registerApplication(Object wicketApplication) {
        this.wicketApplication = wicketApplication;

        LOGGER.info("Plugin {} initialized for application {}", getClass(),
                wicketApplication);
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properties")
    public void clearLocalizerCaches() {
        scheduler.scheduleCommand(this::clearCache);
    }

    private void clearCache() {
        LOGGER.debug("Refreshing Wicket localizer cache.");
        try {
            final Object localizer = getLocalizer();
            final Method clearCacheMethod = resolveClass("org.apache.wicket.Localizer")
                    .getDeclaredMethod("clearCache");
            clearCacheMethod.invoke(localizer);
        } catch (Exception e) {
            LOGGER.error("Error refreshing Wicket localizer cache", e);
        }
    }

    private Object getLocalizer() {
        try {
            final Method getResourceSettingsMethod = resolveClass("org.apache.wicket.Application")
                    .getDeclaredMethod("getResourceSettings");
            final Method getLocalizerMethod = resolveClass("org.apache.wicket.settings.ResourceSettings")
                    .getDeclaredMethod("getLocalizer");
            final Object resourceSettings = getResourceSettingsMethod.invoke(wicketApplication);
            return getLocalizerMethod.invoke(resourceSettings);
        } catch (Exception e) {
            LOGGER.error("Error getting Wicket localizer", e);
            return null;
        }
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
