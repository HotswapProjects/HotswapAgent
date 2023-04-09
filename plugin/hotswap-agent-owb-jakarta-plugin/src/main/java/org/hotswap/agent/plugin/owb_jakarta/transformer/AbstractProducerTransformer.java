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
package org.hotswap.agent.plugin.owb_jakarta.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.FieldAccess;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Hook into AbstractProducerTransformer defineInterceptorStack to keep methodInterceptors instance
 */
public class AbstractProducerTransformer {

    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.portable.AbstractProducer")
    public static void patchProxyFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        CtMethod getProxyClassMethod = ctClass.getDeclaredMethod("defineInterceptorStack");
        getProxyClassMethod.instrument(
            new ExprEditor() {
                public void edit(FieldAccess e) throws CannotCompileException {
                    if (e.isWriter() && "methodInterceptors".equals(e.getFieldName())) {
                        e.replace("{ " +
                            "if($0.methodInterceptors==null) $0.methodInterceptors=new java.util.HashMap();" +
                            "$0.methodInterceptors.clear();" +
                            "$0.methodInterceptors.putAll($1);" +
                        "}");
                    }
                }
            });
    }

}
