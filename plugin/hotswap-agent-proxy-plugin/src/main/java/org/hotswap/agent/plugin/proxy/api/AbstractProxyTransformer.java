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
package org.hotswap.agent.plugin.proxy.api;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.ProxyClassSignatureHelper;

/**
 * Redefines a proxy
 *
 * @author Erki Ehtla
 *
 */
public abstract class AbstractProxyTransformer implements ProxyTransformer
{
    public AbstractProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool) {
        super();
        this.classBeingRedefined = classBeingRedefined;
        this.classPool = classPool;
    }

    protected ProxyBytecodeGenerator generator;
    protected ProxyBytecodeTransformer transformer;
    protected Class<?> classBeingRedefined;
    protected ClassPool classPool;

    protected ProxyBytecodeGenerator getGenerator() {
        if (generator == null) {
            generator = createGenerator();
        }
        return generator;
    }

    protected ProxyBytecodeTransformer getTransformer() {
        if (transformer == null) {
            transformer = createTransformer();
        }
        return transformer;
    }

    /**
     * creates a new ProxyBytecodeGenerator insatance for use in this transformer
     *
     * @return
     */
    protected abstract ProxyBytecodeGenerator createGenerator();

    /**
     * creates a new ProxyBytecodeTransformer insatance for use in this transformer
     *
     * @return
     */
    protected abstract ProxyBytecodeTransformer createTransformer();

    /**
     * Checks if there were changes that require the redefinition of the proxy
     *
     * @return true if there wre changes that require redefinition
     */
    protected boolean isTransformingNeeded() {
        return ProxyClassSignatureHelper.isNonSyntheticPoolClassOrParentDifferent(classBeingRedefined, classPool);
    }

}