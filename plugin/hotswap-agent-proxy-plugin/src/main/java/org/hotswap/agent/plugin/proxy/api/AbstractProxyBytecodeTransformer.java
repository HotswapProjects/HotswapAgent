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

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;

/**
 * Transforms the bytecode of a new proxy definition so it is initialized on the first access of one of its methods.
 *
 * @author Erki Ehtla
 *
 */
public abstract class AbstractProxyBytecodeTransformer implements ProxyBytecodeTransformer
{
    private ClassPool classPool;

    /**
     *
     * @param classPool
     *            Classpool used to make a CtClass
     */
    public AbstractProxyBytecodeTransformer(ClassPool classPool) {
        this.classPool = classPool;
    }

    public byte[] transform(byte[] byteCode) throws Exception {
        CtClass cc = classPool.makeClass(new ByteArrayInputStream(byteCode), false);
        try {
            String initFieldName = INIT_FIELD_PREFIX + generateRandomString();
            addStaticInitStateField(cc, initFieldName);

            String initCode = getInitCall(cc, initFieldName);

            addInitCallToMethods(cc, initFieldName, initCode);
            return cc.toBytecode();
        } finally {
            cc.detach();
        }
    }

    /**
     * Builds the Java code String which should be executed to initialize the proxy
     *
     * @param cc
     *            CtClass from new definition
     * @param random
     *            randomly generated String
     * @return Java code to call init the proxy
     */
    protected abstract String getInitCall(CtClass cc, String random) throws Exception;

    protected String generateRandomString() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Adds the initCall as Java code to all the non static methods of the class. The initialization is only done if
     * clinitFieldName is false. Responsibility to set the clinitFieldName is on the initCall.
     *
     * @param cc
     *            CtClass to be modified
     * @param clinitFieldName
     *            field name in CtClass
     * @param initCall
     *            Java code to initialize the Proxy
     * @throws Exception
     */
    protected void addInitCallToMethods(CtClass cc, String clinitFieldName, String initCall) throws Exception {
        CtMethod[] methods = cc.getDeclaredMethods();
        for (CtMethod ctMethod : methods) {
            if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
                ctMethod.insertBefore("if(!" + clinitFieldName + "){synchronized(" + cc.getName() + ".class){if(!"
                        + clinitFieldName + "){" + initCall + "}}}");
            }
        }
    }

    /**
     * Adds a static boolean field to the class indicating the state of initialization
     *
     * @param cc
     *            CtClass to be modified
     * @param clinitFieldName
     *            field name in CtClass
     * @throws Exception
     */
    protected void addStaticInitStateField(CtClass cc, String clinitFieldName) throws Exception {
        CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
        f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
        // init value "true" will be inside clinit, so the field wont actually be initialized on redefinition
        cc.addField(f, "true");
    }
}
