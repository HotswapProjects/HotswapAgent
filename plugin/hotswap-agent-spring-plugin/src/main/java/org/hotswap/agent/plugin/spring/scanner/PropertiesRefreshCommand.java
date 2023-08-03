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

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Objects;

/**
 * Refresh spring bean when the relevant property file is changed.
 */
public class PropertiesRefreshCommand extends MergeableCommand {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(PropertiesRefreshCommand.class);

    private final URL url;
    private final ClassLoader appClassLoader;

    public PropertiesRefreshCommand(ClassLoader classLoader, URL url) {
        this.url = url;
        this.appClassLoader = classLoader;
    }

    /**
     * Pass changed property file to XmlBeanDefinitionScannerAgent, the agent will decide whether to reload because the
     * corresponding placeholder property files are changed.
     *
     * @see XmlBeanDefinitionScannerAgent#reloadProperty(URL)
     */
    @Override
    public void executeCommand() {
        try {
//            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent", true, appClassLoader);
//            Method method = clazz.getDeclaredMethod("reloadProperty", URL.class);
//            method.invoke(null, url);
        } catch (Throwable t) {
            LOGGER.error("Error refreshing property file {} in classLoader {}", t, this.url, appClassLoader);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertiesRefreshCommand that = (PropertiesRefreshCommand) o;
        return Objects.equals(url, that.url) && Objects.equals(appClassLoader, that.appClassLoader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, appClassLoader);
    }

    @Override
    public String toString() {
        return "PropertiesRefreshCommand{" +
                "url=" + url +
                ", appClassLoader=" + appClassLoader +
                '}';
    }
}
