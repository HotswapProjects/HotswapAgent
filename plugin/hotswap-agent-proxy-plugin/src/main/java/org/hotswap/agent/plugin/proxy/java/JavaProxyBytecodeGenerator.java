/*
 * Copyright 2013-2022 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Creates a new bytecode for a Java proxy. Changed Classes have to be already loaded in the App classloader.
 *
 * @author Erki Ehtla
 *
 */
public class JavaProxyBytecodeGenerator implements ProxyBytecodeGenerator {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JavaProxyBytecodeGenerator.class);

    private Class<?> classBeingRedefined;

    public JavaProxyBytecodeGenerator(Class<?> classBeingRedefined) {
        super();
        this.classBeingRedefined = classBeingRedefined;
    }

    public byte[] generate() throws Exception {
        Class<?> proxyGeneratorClass = null;

        try {
            // java9
            proxyGeneratorClass = getClass().getClassLoader().loadClass("java.lang.reflect.ProxyGenerator");
        } catch (ClassNotFoundException e) {
            try {
                proxyGeneratorClass = getClass().getClassLoader().loadClass("sun.misc.ProxyGenerator");
            } catch (ClassNotFoundException ex) {
                LOGGER.error("Unable to loadClass ProxyGenerator!");
                return null;
            }
        }

        return (byte[]) ReflectionHelper.invoke(null, proxyGeneratorClass, "generateProxyClass", new Class[] {String.class, Class[].class },
                classBeingRedefined.getName(), classBeingRedefined.getInterfaces());
    }
}
