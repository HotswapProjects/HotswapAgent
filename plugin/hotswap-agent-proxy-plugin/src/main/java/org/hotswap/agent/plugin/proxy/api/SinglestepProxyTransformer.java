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
import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxy transformations that can be done in one step
 *
 * @author Erki Ehtla
 *
 */
public abstract class SinglestepProxyTransformer extends AbstractProxyTransformer
{
    private static final AgentLogger LOGGER = AgentLogger.getLogger(SinglestepProxyTransformer.class);

    protected byte[] classfileBuffer;

    public SinglestepProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool, byte[] classfileBuffer) {
        super(classBeingRedefined, classPool);
        this.classfileBuffer = classfileBuffer;
    }

    /**
     * Handles the current transformation state
     *
     * @return
     * @throws Exception
     */
    public byte[] transformRedefine() throws Exception {
        if (!isTransformingNeeded()) {
            return classfileBuffer;
        }
        classfileBuffer = getTransformer().transform(getGenerator().generate());
        LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
        return classfileBuffer;
    }
}
