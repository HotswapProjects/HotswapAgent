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

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Created by bubnik on 5.6.2014.
 */
public class TomcatSetup {
    private static AgentLogger LOGGER = AgentLogger.getLogger(TomcatSetup.class);

    public TomcatSetup(ClassLoader originalClassLoader) {

        System.err.println("PluginManagerInstance = " + PluginManager.getInstance());
        PluginManagerInvoker.callInitializePlugin(TomcatPlugin.class, originalClassLoader);

        //PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader, "init"
        URL[] extraClassPath = (URL[]) PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader,
                "getExtraClassPath", new Class[] {}, new Object[] {});
        System.err.println("extraClassPath =  " + Arrays.toString(extraClassPath));
        LOGGER.debug("extraClassPath = {}", extraClassPath);


        if (extraClassPath.length > 0) {
            LOGGER.debug("Registering extraClasspath {} to classloader {}", extraClassPath, originalClassLoader);
            for (URL url : extraClassPath) {
                // classLoader.addRepository(classesPath + "/", classRepository);
                try {
                    File classRepository = new File(url.toURI());

                    Method m = originalClassLoader.getClass().getDeclaredMethod("addRepository", String.class, File.class);
                    m.setAccessible(true);
                    m.invoke(originalClassLoader, classRepository.getAbsolutePath() + "/", classRepository);
                } catch (Exception e) {
                    throw new Error(e);
                }

            }
        }
    }


}
