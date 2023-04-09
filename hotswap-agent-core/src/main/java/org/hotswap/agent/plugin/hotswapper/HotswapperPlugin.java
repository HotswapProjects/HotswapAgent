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
package org.hotswap.agent.plugin.hotswapper;

import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.*;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Hotswap class changes directly via JPDA API.
 * <p/>
 * This plugin creates an instance for each classloader with autoHotswap agent property set. Then it listens
 * for .class file change and executes hotswap via JPDA API.
 *
 * @author Jiri Bubnik
 * @see HotSwapperJpda
 */
@Plugin(name = "Hotswapper", description = "Watch for any class file change and reload (hotswap) it on the fly.",
        testedVersions = {"JDK 1.7.0_45"}, expectedVersions = {"JDK 1.6+"})
public class HotswapperPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapperPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    PluginManager pluginManager;

    // synchronize on this map to wait for previous processing
    final Map<Class<?>, byte[]> reloadMap = new HashMap<>();

    // command to do actual hotswap. Single command to merge possible multiple reload actions.
    Command hotswapCommand;

    /**
     * For each changed class create a reload command.
     */
    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.MODIFY, FileEvent.CREATE})
    public void watchReload(CtClass ctClass, ClassLoader appClassLoader, URL url) throws IOException, CannotCompileException {
        if (!ClassLoaderHelper.isClassLoaded(appClassLoader, ctClass.getName())) {
            LOGGER.trace("Class {} not loaded yet, no need for autoHotswap, skipped URL {}", ctClass.getName(), url);
            return;
        }

        LOGGER.debug("Class {} will be reloaded from URL {}", ctClass.getName(), url);

        // search for a class to reload
        Class clazz;
        try {
            clazz  = appClassLoader.loadClass(ctClass.getName());
        } catch (ClassNotFoundException e) {
            LOGGER.warning("Hotswapper tries to reload class {}, which is not known to application classLoader {}.",
                    ctClass.getName(), appClassLoader);
            return;
        }

        synchronized (reloadMap) {
            reloadMap.put(clazz, ctClass.toBytecode());
        }
        scheduler.scheduleCommand(hotswapCommand, 100, Scheduler.DuplicateSheduleBehaviour.SKIP);
    }

    /**
     * Create a hotswap command using hotSwappper.
     *
     * @param appClassLoader it can be run in any classloader with tools.jar on classpath. AppClassLoader can
     *                       be setup by maven dependency (jetty plugin), use this classloader.
     * @param port           attach the hotswapper
     */
    public void initHotswapCommand(ClassLoader appClassLoader, String port) {
        if (port != null && port.length() > 0) {
            hotswapCommand = new ReflectionCommand(this, HotswapperCommand.class.getName(), "hotswap", appClassLoader,
                    port, reloadMap);
        } else {
            hotswapCommand = new Command() {
                @Override
                public void executeCommand() {
                    pluginManager.hotswap(reloadMap);
                }

                @Override
                public String toString() {
                    return "pluginManager.hotswap(" + Arrays.toString(reloadMap.keySet().toArray()) + ")";
                }
            };
        }
    }

    /**
     * For each classloader check for autoHotswap configuration instance with hotswapper.
     */
    @Init
    public static void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {

        if (appClassLoader == null) {
            LOGGER.debug("Bootstrap class loader is null, hotswapper skipped.");
            return;
        }

        LOGGER.debug("Init plugin at classLoader {}", appClassLoader);

        // init only if the classloader contains directly the property file (not in parent classloader)
        if (!HotswapAgent.isAutoHotswap() && !pluginConfiguration.containsPropertyFile()) {
            LOGGER.debug("ClassLoader {} does not contain hotswap-agent.properties file, hotswapper skipped.", appClassLoader);
            return;
        }

        // and autoHotswap enabled
        if (!HotswapAgent.isAutoHotswap() && !pluginConfiguration.getPropertyBoolean("autoHotswap")) {
            LOGGER.debug("ClassLoader {} has autoHotswap disabled, hotswapper skipped.", appClassLoader);
            return;
        }


        String port = pluginConfiguration.getProperty("autoHotswap.port");

        HotswapperPlugin plugin = PluginManagerInvoker.callInitializePlugin(HotswapperPlugin.class, appClassLoader);
        if (plugin != null) {
            plugin.initHotswapCommand(appClassLoader, port);
        } else {
            LOGGER.debug("Hotswapper is disabled in {}", appClassLoader);
        }
    }
}
