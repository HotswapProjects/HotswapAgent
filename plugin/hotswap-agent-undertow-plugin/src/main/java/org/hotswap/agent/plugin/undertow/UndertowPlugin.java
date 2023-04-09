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
package org.hotswap.agent.plugin.undertow;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Undertow plugin (http://undertow.io/)
 *
 * @author Vladimir Dvorak
 *
 */
@Plugin(name = "Undertow", description = "Undertow plugin.",
        testedVersions = {"2.0.19"},
        expectedVersions = {"2.0"},
        supportClass={UndertowTransformer.class}
)
public class UndertowPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(UndertowPlugin.class);

    // Undertow resource manager object to a application classloader
    static Map<Object, ClassLoader> registeredResourceManagersMap = new HashMap<>();

    // For each app classloader map of undertow repository name to associated watch resource classloader
    private static Map<ClassLoader, Map<String, ClassLoader>> extraRepositories = new HashMap<>();

    String undertowVersion = "";

    /**
     * Init the plugin during DeploymentManagerImpl.deploy lifecycle. This method is invoked before the plugin is initialized.
     * @param appClassLoader main web deplyment classloader.
     * @param resourceManager undertow resource manager associated to deployment
     */
    public static void init(ClassLoader appClassLoader, Object resourceManager) {

        String version = resolveUndertowVersion(appClassLoader);
        registeredResourceManagersMap.put(resourceManager, appClassLoader);

        // create plugin configuration in advance to get extraClasspath and watchResources properties
        PluginConfiguration pluginConfiguration = new PluginConfiguration(appClassLoader);

        List<File> extraResources = new ArrayList<>();

        addExtraResources(extraResources, pluginConfiguration.getExtraClasspath());
        addExtraResources(extraResources, pluginConfiguration.getWatchResources());
        addExtraResources(extraResources, pluginConfiguration.getWebappDir());

        try {
            ReflectionHelper.invoke(resourceManager, resourceManager.getClass(), "setExtraResources", new Class[] { List.class }, extraResources);
        } catch (Exception e) {
            LOGGER.error("init() exception {}.", e.getMessage());
        }

        Object plugin = PluginManagerInvoker.callInitializePlugin(UndertowPlugin.class, appClassLoader);
        if (plugin != null) {
            ReflectionHelper.invoke(plugin, plugin.getClass(), "init", new Class[]{String.class, ClassLoader.class}, version, appClassLoader);
        } else {
            LOGGER.debug("UndertowPlugin is disabled in {}", appClassLoader);
        }
    }

    private static void addExtraResources(List<File> extraResources, URL[] extraURLs) {
        for (int i=0; i < extraURLs.length; i++) {
            try {
                File file = new File(extraURLs[i].toURI());
                if (file.isDirectory()) {
                    extraResources.add(file);
                }
            } catch (URISyntaxException e) {
                LOGGER.warning("Unable to convert resource URL '{}' to URI. URL is skipped.", e, extraURLs[i]);
            }
        }
    }

    /**
     * Init plugin and resolve  version.
     *
     * @param undertowVersion the undertow version
     * @param appClassLoader the app class loader
     */
    private void init(String undertowVersion, ClassLoader appClassLoader ) {
        LOGGER.info("Undertow plugin initialized - Undertow version '{}'", undertowVersion);
        this.undertowVersion = undertowVersion;
    }

    /**
     * Close plugin
     *
     * @param classLoader the class loader
     */
    public static void close(ClassLoader classLoader) {
        Map<String, ClassLoader> registerMap = extraRepositories.remove(classLoader);
        if (registerMap != null) {
            for (ClassLoader loader : registerMap.values()) {
                PluginManager.getInstance().getWatcher().closeClassLoader(loader);
            }
        }
    }

    /**
     * Resolve the server version from Version class.
     * @param appClassLoader application classloader
     * @return the server version
     */
    private static String resolveUndertowVersion(ClassLoader appClassLoader) {
        try {
            Class version = appClassLoader.loadClass("io.undertow.Version");
            return (String) ReflectionHelper.invoke(null, version, "getVersionString", new Class[]{});
        } catch (Exception e) {
            LOGGER.debug("Unable to resolve undertow version", e);
            return "unknown";
        }
    }
}
