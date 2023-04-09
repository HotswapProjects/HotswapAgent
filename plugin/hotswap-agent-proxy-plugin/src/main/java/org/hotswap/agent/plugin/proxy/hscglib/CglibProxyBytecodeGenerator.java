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

import java.lang.reflect.Method;

import org.hotswap.agent.plugin.proxy.api.ProxyBytecodeGenerator;

/**
 * Creates new bytecode for a Cglib proxy. Changed Classes have to be loaded
 * alreadyin the App classloader.
 *
 * @author Erki Ehtla
 *
 */
public class CglibProxyBytecodeGenerator implements ProxyBytecodeGenerator {
    private GeneratorParams params;

    public CglibProxyBytecodeGenerator(GeneratorParams params) {
        super();
        this.params = params;
    }

    public byte[] generate() throws Exception {
        Method genMethod = getGenerateMethod(params.getGenerator());
        if (genMethod == null)
            throw new RuntimeException(
                    "No generation Method found for redefinition!");
        return (byte[]) genMethod.invoke(params.getGenerator(),
                params.getParam());
    }

    /**
     * Retrieves the actual Method that generates and returns the bytecode
     *
     * @param generator
     *            GeneratorStrategy instance
     * @return Method that generates and returns the bytecode
     */
    private Method getGenerateMethod(Object generator) {
        Method[] methods = generator.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals("generate")
                    && method.getReturnType().getSimpleName().equals("byte[]")
                    && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }
}
