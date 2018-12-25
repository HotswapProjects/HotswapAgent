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
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.plugin.proxy.AbstractProxyBytecodeTransformer;

/**
 * Transforms the bytecode of a new Java proxy definition so it is initialized
 * on the first access of one of its methods.
 *
 * @author Erki Ehtla
 *
 */
public class JavaProxyBytecodeTransformer
        extends AbstractProxyBytecodeTransformer {
    public JavaProxyBytecodeTransformer(ClassPool classPool) {
        super(classPool);
    }

    @Override
    protected String getInitCall(CtClass cc, String initFieldName)
            throws Exception {
        // clinit method already contains the setting of our static
        // clinitFieldName to true
        CtMethod method = cc.getClassInitializer().toMethod(initFieldName, cc);
        method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
        cc.addMethod(method);
        return method.getName() + "();";
    }
}
