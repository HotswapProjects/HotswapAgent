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

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.api.ProxyBytecodeGenerator;
import org.hotswap.agent.plugin.proxy.api.ProxyBytecodeTransformer;
import org.hotswap.agent.plugin.proxy.api.SinglestepProxyTransformer;

/**
 * Redefines Cglib Enhancer proxy classes. Uses
 * CglibEnhancerProxyBytecodeGenerator for the bytecode generation.
 *
 * @author Erki Ehtla
 *
 */
public class CglibEnhancerProxyTransformer extends SinglestepProxyTransformer {

    private GeneratorParams params;
    private ClassLoader loader;

    /**
     *
     * @param classBeingRedefined
     * @param classPool
     *            Classpool of the classloader
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param loader
     *            classloader of classBeingRedefined
     * @param params
     *            parameters used to generate proxy
     * @throws IllegalClassFormatException
     */
    public CglibEnhancerProxyTransformer(Class<?> classBeingRedefined,
            ClassPool classPool, byte[] classfileBuffer, ClassLoader loader,
            GeneratorParams params) {
        super(classBeingRedefined, classPool, classfileBuffer);
        this.loader = loader;
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
            ClassPool classPool, byte[] classfileBuffer, ClassLoader loader,
            GeneratorParams params) throws Exception {
        return new CglibEnhancerProxyTransformer(classBeingRedefined, classPool,
                classfileBuffer, loader, params).transformRedefine();
    }

    @Override
    protected ProxyBytecodeGenerator createGenerator() {
        return new CglibEnhancerProxyBytecodeGenerator(params, loader);
    }

    @Override
    protected ProxyBytecodeTransformer createTransformer() {
        return new CglibProxyBytecodeTransformer(classPool);
    }
}
