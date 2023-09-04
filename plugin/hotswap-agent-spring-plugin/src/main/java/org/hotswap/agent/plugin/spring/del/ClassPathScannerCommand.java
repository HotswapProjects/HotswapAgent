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
//import org.hotswap.agent.command.ReflectionCommand;
//
///**
// * Standard reflection command, but group same command by basePath and resource.
// */
//@Deprecated
//public class ClassPathScannerCommand extends ReflectionCommand {
//    String basePath;
//    byte[] classDefinition;
//
//    public ClassPathScannerCommand(Object plugin, String className, String methodName, ClassLoader targetClassLoader,
//                                   String basePath, byte[] classDefinition) {
//        super(plugin, className, methodName, targetClassLoader, basePath, classDefinition);
//        this.basePath = basePath;
//        this.classDefinition = classDefinition;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        ClassPathScannerCommand that = (ClassPathScannerCommand) o;
//
//        if (!basePath.equals(that.basePath)) return false;
//        if (!classDefinition.equals(that.classDefinition)) return false;
//
//        return true;
//    }
//
//    @Override
//    public int hashCode() {
//        int result = basePath.hashCode();
//        result = 31 * result + classDefinition.hashCode();
//        return result;
//    }
//}
