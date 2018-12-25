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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.MultistepProxyTransformer;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

/**
 * Redefines Java proxy classes. Uses several redefinition events
 *
 * @author Erki Ehtla
 *
 */
public class JavaProxyTransformer extends MultistepProxyTransformer {
    // Class transformation states for all the ClassLoaders. Used in the Agent
    // ClassLoader
    private static final Map<Class<?>, TransformationState> TRANSFORMATION_STATES = Collections
            .synchronizedMap(new WeakHashMap<Class<?>, TransformationState>());

    /**
     *
     * @param classBeingRedefined
     * @param cp
     *            Classpool of the classloader
     * @param classfileBuffer
     *            new definition of Class<?>
     */
    public JavaProxyTransformer(Class<?> classBeingRedefined, ClassPool cp,
            byte[] classfileBuffer) {
        super(classBeingRedefined, cp, classfileBuffer, TRANSFORMATION_STATES);
    }

    /**
     *
     * @param classBeingRedefined
     * @param cp
     *            Classpool of the classloader
     * @param classfileBuffer
     *            new definition of Class<?>
     * @return classfileBuffer or new Proxy defition if there are signature
     *         changes
     * @throws Exception
     */
    public static byte[] transform(Class<?> classBeingRedefined, ClassPool cp,
            byte[] classfileBuffer) throws Exception {
        return new JavaProxyTransformer(classBeingRedefined, cp,
                classfileBuffer).transformRedefine();
    }

    public static boolean isReloadingInProgress() {
        return !TRANSFORMATION_STATES.isEmpty();
    }

    @Override
    protected ProxyBytecodeGenerator createGenerator() {
        return new JavaProxyBytecodeGenerator(classBeingRedefined);
    }

    @Override
    protected ProxyBytecodeTransformer createTransformer() {
        return new JavaProxyBytecodeTransformer(classPool);
    }
}
