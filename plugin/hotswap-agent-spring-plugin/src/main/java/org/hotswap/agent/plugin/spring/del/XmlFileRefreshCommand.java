///*
// * Copyright 2013-2023 the HotswapAgent authors.
// *
// * This file is part of HotswapAgent.
// *
// * HotswapAgent is free software: you can redistribute it and/or modify it
// * under the terms of the GNU General Public License as published by the
// * Free Software Foundation, either version 2 of the License, or (at your
// * option) any later version.
// *
// * HotswapAgent is distributed in the hope that it will be useful, but
// * WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// * Public License for more details.
// *
// * You should have received a copy of the GNU General Public License along
// * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
// */
//package org.hotswap.agent.plugin.spring.scanner;
//
//import org.hotswap.agent.command.MergeableCommand;
//import org.hotswap.agent.logging.AgentLogger;
//
//import java.io.File;
//import java.lang.reflect.Method;
//import java.net.URL;
//import java.util.Objects;
//
///**
// * Refresh all Spring beans (scanned by XML) based on XML files when XML file is changed.
// * <p>
// * This commands merges events of watcher.event(CREATE) and transformer hotswap reload to a single refresh command.
// */
//public class XmlFileRefreshCommand extends MergeableCommand {
//    private final static AgentLogger LOGGER = AgentLogger.getLogger(XmlFileRefreshCommand.class);
//
//    /**
//     * path to spring xml
//     */
//    private final URL url;
//
//    private final ClassLoader appClassLoader;
//
//    public XmlFileRefreshCommand(ClassLoader appClassLoader, URL url) {
//        this.appClassLoader = appClassLoader;
//        this.url = url;
//    }
//
//    /**
//     * Reloads Spring XML file.
//     *
//     * @see XmlBeanDefinitionScannerAgent#reloadXml(URL)
//     */
//    @Override
//    public void executeCommand() {
//        if (!new File(url.getPath()).exists()) {
//            LOGGER.trace("Skip Spring reload for delete event on file '{}'", url);
//            return;
//        }
//
//        LOGGER.info("Executing XmlBeanDefinitionScannerAgent.reloadXml('{}')", url);
//
//        try {
//            // not using Class.getName() to get class name
//            // because it will cause common classloader to load XmlBeanDefinitionScannerAgent
//            // which may cause problem in multi-app scenario.
//            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.xml.XmlBeanDefinitionScannerAgent",
//                    true, appClassLoader);
//            Method method = clazz.getDeclaredMethod("reloadXml", URL.class);
//            method.invoke(null, this.url);
//        } catch (Throwable t) {
//            LOGGER.error("Error refreshing Spring XML {} in classLoader {}", t, this.url, appClassLoader);
//        }
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        XmlFileRefreshCommand that = (XmlFileRefreshCommand) o;
//        return Objects.equals(url, that.url) && Objects.equals(appClassLoader, that.appClassLoader);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(url, appClassLoader);
//    }
//
//    @Override
//    public String toString() {
//        return "XmlFileRefreshCommand{" +
//                "url=" + url +
//                ", appClassLoader=" + appClassLoader +
//                '}';
//    }
//}
