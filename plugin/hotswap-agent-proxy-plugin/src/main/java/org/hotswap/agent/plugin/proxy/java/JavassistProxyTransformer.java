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
package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyTransformer;

/**
 * Redefines Java proxy classes. One-step process. Uses CtClasses from the ClassPool.
 *
 * @author Erki Ehtla
 *
 */
public class JavassistProxyTransformer implements ProxyTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JavassistProxyTransformer.class);
    private final Class<?> classBeingRedefined;
    private final CtClass cc;
    private final ClassPool cp;

    /**
     * Instantiates a new javassist proxy transformer.
     *
     * @param classBeingRedefined the class being redefined
     * @param cc            CtClass from classfileBuffer
     * @param cp            Classpool of the classloader
     * @return classfileBuffer or new Proxy defition if there are signature changes
     */
    public JavassistProxyTransformer(Class<?> classBeingRedefined, CtClass cc, ClassPool cp) {
        super();
        this.classBeingRedefined = classBeingRedefined;
        this.cc = cc;
        this.cp = cp;
    }

    /**
     *
     * @param classBeingRedefined
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param cc
     *            CtClass from classfileBuffer
     * @param cp
     *            Classpool of the classloader
     * @return classfileBuffer or new Proxy defition if there are signature changes
     * @throws Exception
     */
    public static byte[] transform(final Class<?> classBeingRedefined, CtClass cc, ClassPool cp) throws Exception {
        return new JavassistProxyTransformer(classBeingRedefined, cc, cp).transformRedefine();
    }

    @Override
    public byte[] transformRedefine() throws Exception {
        try {
            byte[] generateProxyClass = CtClassJavaProxyGenerator.generateProxyClass(classBeingRedefined.getName(), cc.getInterfaces(), cp);
            LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
            return generateProxyClass;
        } catch (Exception e) {
            LOGGER.error("Error transforming a Java reflect Proxy", e);
        }
        return null;
    }
}