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
package org.hotswap.agent.plugin.tomcat;

import java.net.URL;
import java.util.Arrays;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;

/**
 * Created by bubnik on 4.6.2014.
 */
public class WatcherTomcatWebappClassLoader extends WatchResourcesClassLoader {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatcherTomcatWebappClassLoader.class);

    public WatcherTomcatWebappClassLoader(ClassLoader originalClassLoader) {
        super(originalClassLoader);

        PluginManagerInvoker.callInitializePlugin(TomcatPlugin.class, originalClassLoader);

        //PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader, "init"
        URL[] extraClassPath = (URL[]) PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader,
                "getExtraClassPath", new Class[] {}, new Object[] {});

        LOGGER.debug("extraClassPath = {}", extraClassPath);
        if (extraClassPath.length > 0) {
            LOGGER.debug("Registering extraClasspath {} to classloader {}", extraClassPath, originalClassLoader);
            initExtraPath(extraClassPath);
        }

        URL[] watchResources = (URL[]) PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader,
                "getWatchResources", new Class[] {}, new Object[] {});

        System.err.println("watchResources =  " + Arrays.toString(watchResources));
        LOGGER.debug("watchResources = {}", watchResources);
        if (watchResources.length > 0) {
            LOGGER.debug("Registering watchResources {} to classloader {}", extraClassPath, originalClassLoader);
            initWatchResources(watchResources, PluginManager.getInstance().getWatcher());
        }
    }
}
