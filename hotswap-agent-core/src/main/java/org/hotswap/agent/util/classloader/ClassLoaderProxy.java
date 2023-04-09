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
package org.hotswap.agent.util.classloader;

import org.hotswap.agent.javassist.CtClass;

/**
 * @author Jiri Bubnik
 */
@Deprecated
public class ClassLoaderProxy {

    ClassLoader targetClassLoader;

    public CtClass create(CtClass classToProxy) throws Exception {
//        CtPool ctPool = classToProxy;
//        ProxyFactory factory = new ProxyFactory();
//        factory.setSuperclass(classToProxy);
//        factory.
//
//        Class proxy = factory.createClass();
//
//        new ClassFile()
//
//
//        MethodHandler handler = new MethodHandler() {
//
//            @Override
//            public Object invoke(Object self, Method overridden, Method forwarder,
//                                 Object[] args) throws Throwable {
//                System.out.println("do something "+overridden.getName());
//
//                Class classInTargetClassLoader = targetClassLoader.loadClass(classToProxy.getName());
//                Method methodInTargetClassLoader = classInTargetClassLoader.getDeclaredMethod(
//                        overridden.getName(), overridden.getParameterTypes()
//                );
//
//                Class returnType = overridden.getReturnType();
//
//                return methodInTargetClassLoader.invoke(null, args);
//            }
//        };
//        Object instance = proxy.newInstance();
//        ((ProxyObject) instance).setHandler(handler);
//        return (T) instance;
        return null;
    }

}
