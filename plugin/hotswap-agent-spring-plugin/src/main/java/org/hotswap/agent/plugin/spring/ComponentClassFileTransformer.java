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
//package org.hotswap.agent.plugin.spring;
//
//import org.hotswap.agent.command.Scheduler;
//import org.hotswap.agent.plugin.spring.reader.ComponentClassRefreshCommand;
//import org.hotswap.agent.util.HaClassFileTransformer;
//
//import java.lang.instrument.IllegalClassFormatException;
//import java.security.ProtectionDomain;
//import java.util.Objects;
//
//public class ComponentClassFileTransformer implements HaClassFileTransformer {
//    private final ClassLoader appClassLoader;
//    private final Scheduler scheduler;
//    private final String componentClass;
//
//    public ComponentClassFileTransformer(ClassLoader appClassLoader, Scheduler scheduler, String componentClass) {
//        this.appClassLoader = appClassLoader;
//        this.scheduler = scheduler;
//        this.componentClass = componentClass;
//    }
//
//    @Override
//    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
//                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
//        final SpringChangesAnalyzer analyzer = new SpringChangesAnalyzer(appClassLoader);
//        if (classBeingRedefined != null && className.replace('/', '.').equals(componentClass)) {
//            if (analyzer.isReloadNeeded(classBeingRedefined, classfileBuffer)) {
//                scheduler.scheduleCommand(new ComponentClassRefreshCommand(classBeingRedefined.getClassLoader(),
//                        componentClass, classfileBuffer));
//            }
//        }
//
//        return classfileBuffer;
//    }
//
//    @Override
//    public boolean isForRedefinitionOnly() {
//        return true;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        ComponentClassFileTransformer that = (ComponentClassFileTransformer) o;
//        return Objects.equals(appClassLoader, that.appClassLoader) && Objects.equals(componentClass, that.componentClass);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(appClassLoader, componentClass);
//    }
//}
