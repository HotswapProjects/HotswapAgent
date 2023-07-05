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
package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

public class PropertiesRefreshCommand extends MergeableCommand {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(PropertiesRefreshCommand.class);

    private final URL url;
    private final ClassLoader appClassLoader;

    public PropertiesRefreshCommand(ClassLoader classLoader, URL url) {
        this.url = url;
        this.appClassLoader = classLoader;
    }

    @Override
    public void executeCommand() {
        try {
            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent", true, appClassLoader);
            Method method = clazz.getDeclaredMethod("reloadProperty", URL.class);
            method.invoke(null, url);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing property file {} in classLoader {}", e, this.url, appClassLoader);
        }
    }
}
