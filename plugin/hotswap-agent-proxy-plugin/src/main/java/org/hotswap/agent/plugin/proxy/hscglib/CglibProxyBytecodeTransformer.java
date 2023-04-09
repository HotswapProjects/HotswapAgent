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

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.plugin.proxy.api.AbstractProxyBytecodeTransformer;

/**
 * Transforms the bytecode of a new Cglib proxy definition so it is initialized
 * on the first access of one of its methods.
 *
 * @author Erki Ehtla
 *
 */
public class CglibProxyBytecodeTransformer
        extends AbstractProxyBytecodeTransformer {
    public CglibProxyBytecodeTransformer(ClassPool classPool) {
        super(classPool);
    }

    @Override
    protected String getInitCall(CtClass cc, String initFieldName)
            throws Exception {
        CtMethod[] methods = cc.getDeclaredMethods();
        StringBuilder strB = new StringBuilder();
        for (CtMethod ctMethod : methods) {
            if (ctMethod.getName().startsWith("CGLIB$STATICHOOK")) {
                ctMethod.insertAfter(initFieldName + "=true;");
                strB.insert(0, ctMethod.getName() + "();");
                break;
            }
        }

        if (strB.length() == 0)
            throw new RuntimeException(
                    "Could not find CGLIB$STATICHOOK method");
        return strB.toString() + "CGLIB$BIND_CALLBACKS(this);";
    }
}
