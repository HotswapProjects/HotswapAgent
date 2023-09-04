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
//import java.lang.reflect.Method;
//import java.util.Objects;
//
///**
// * Refresh spring bean defined in XML file when individual class is changed.
// */
//public class XmlBeanRefreshCommand extends MergeableCommand {
//    private static final AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanRefreshCommand.class);
//
//    private final ClassLoader appClassLoader;
//    private final String className;
//
//    public XmlBeanRefreshCommand(ClassLoader appClassLoader, String className) {
//        this.appClassLoader = appClassLoader;
//        this.className = className;
//    }
//
//    /**
//     * Pass changed class to XmlBeanDefinitionScannerAgent, the agent will decide whether to reload the bean definition.
//     *
//     * @see XmlBeanDefinitionScannerAgent#reloadClass(String)
//     */
//    @Override
//    public void executeCommand() {
//        LOGGER.info("Executing XmlBeanDefinitionScannerAgent.reloadClass('{}')", className);
//
//        try {
//            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.xml.XmlBeanDefinitionScannerAgent",
//                    true, appClassLoader);
//            Method method = clazz.getDeclaredMethod("reloadClass", String.class);
//            method.invoke(null, this.className);
//        } catch (Throwable t) {
//            LOGGER.error("Error reloading Spring bean {} in classLoader {}", t, this.className, appClassLoader);
//        }
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        XmlBeanRefreshCommand that = (XmlBeanRefreshCommand) o;
//        return Objects.equals(appClassLoader, that.appClassLoader) && Objects.equals(className, that.className);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(appClassLoader, className);
//    }
//
//    @Override
//    public String toString() {
//        return "XmlBeanRefreshCommand{" +
//                "appClassLoader=" + appClassLoader +
//                ", className='" + className + '\'' +
//                '}';
//    }
//}
