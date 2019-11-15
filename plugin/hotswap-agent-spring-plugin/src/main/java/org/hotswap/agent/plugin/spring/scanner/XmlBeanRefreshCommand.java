/*
 * Copyright 2013-2019 the HotswapAgent authors.
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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Do refresh Spring class (scanned by xml) based on xml files.
 *
 * This commands merges events of watcher.event(CREATE) and transformer hotswap reload to a single refresh command.
 */
public class XmlBeanRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanRefreshCommand.class);

    /**
     * path to spring xml
     */
    URL url;

    ClassLoader appClassLoader;

    public XmlBeanRefreshCommand(ClassLoader appClassLoader, URL url) {
        this.appClassLoader = appClassLoader;
        this.url = url;
    }

    @Override
    public void executeCommand() {
        if (!new File(url.getPath()).exists()) {
            LOGGER.trace("Skip Spring reload for delete event on file '{}'", url);
            return;
        }

        LOGGER.info("Executing XmlBeanDefinitionScannerAgent.reloadXml('{}')", url);

        try {
            // not using Class.getName() to get class name
            // because it will cause common classloader to load XmlBeanDefinitionScannerAgent
            // which may cause problem in multi-app scenario.
            Class clazz = Class.forName("org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent", true, appClassLoader);
            Method method  = clazz.getDeclaredMethod(
                    "reloadXml", new Class[] {URL.class});
            method.invoke(null, this.url);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, maybe failed to hook spring method to init plugin with right classLoader", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing xml {} in classLoader {}", e, this.url, appClassLoader);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        XmlBeanRefreshCommand that = (XmlBeanRefreshCommand) o;

        return this.url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return this.url.hashCode();
    }

    @Override
    public String toString() {
        return "XmlBeanRefreshCommand{" +
                "url='" + url + '\'' +
                '}';
    }
}
