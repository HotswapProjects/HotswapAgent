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
package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.instrument.IllegalClassFormatException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.api.MultistepProxyTransformer;
import org.hotswap.agent.plugin.proxy.api.ProxyBytecodeGenerator;
import org.hotswap.agent.plugin.proxy.api.ProxyBytecodeTransformer;
import org.hotswap.agent.plugin.proxy.api.TransformationState;

/**
 * Redefines Cglib proxy classes. Uses several redefinition events
 *
 * @author Erki Ehtla
 *
 */
public class CglibProxyTransformer extends MultistepProxyTransformer {
    // Class transformation states for all the ClassLoaders. Used in the Agent
    // ClassLoader
    private static final Map<Class<?>, TransformationState> TRANSFORMATION_STATES = Collections
            .synchronizedMap(new WeakHashMap<Class<?>, TransformationState>());
    private GeneratorParams params;

    /**
     *
     * @param classBeingRedefined
     * @param classPool
     *            Classpool of the classloader
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param params
     *            parameters used to generate proxy
     * @throws IllegalClassFormatException
     */
    public CglibProxyTransformer(Class<?> classBeingRedefined,
            ClassPool classPool, byte[] classfileBuffer,
            GeneratorParams params) {
        super(classBeingRedefined, classPool, classfileBuffer,
                TRANSFORMATION_STATES);
        this.params = params;
    }

    /**
     *
     * @param classBeingRedefined
     * @param cc
     *            CtClass from classfileBuffer
     * @param cp
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param loader
     *            ClassLoader of the classBeingRedefined
     * @return classfileBuffer or new Proxy defition if there are signature
     *         changes
     * @throws IllegalClassFormatException
     */
    public static byte[] transform(Class<?> classBeingRedefined,
            ClassPool classPool, byte[] classfileBuffer, GeneratorParams params)
            throws Exception {
        return new CglibProxyTransformer(classBeingRedefined, classPool,
                classfileBuffer, params).transformRedefine();
    }

    public static boolean isReloadingInProgress() {
        return !TRANSFORMATION_STATES.isEmpty();
    }

    @Override
    protected ProxyBytecodeGenerator createGenerator() {
        return new CglibProxyBytecodeGenerator(params);
    }

    @Override
    protected ProxyBytecodeTransformer createTransformer() {
        return new CglibProxyBytecodeTransformer(classPool);
    }
}
