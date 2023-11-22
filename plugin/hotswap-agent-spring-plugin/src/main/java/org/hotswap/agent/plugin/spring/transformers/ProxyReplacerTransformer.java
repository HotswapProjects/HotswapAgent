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
package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;

/**
 * Transforms Spring classes so the beans go through this plugin. The returned beans are proxied and tracked. The bean
 * proxies can be reset and reloaded from Spring.
 *
 * @author Erki Ehtla
 *
 */
public class ProxyReplacerTransformer {
    public static final String FACTORY_METHOD_NAME = "getBean";

    private static CtMethod overrideMethod(CtClass ctClass, CtMethod getConnectionMethodOfSuperclass)
            throws NotFoundException, CannotCompileException {
        final CtMethod m = CtNewMethod.delegator(getConnectionMethodOfSuperclass, ctClass);
        ctClass.addMethod(m);
        return m;
    }

    /**
     *
     * @param ctClass
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
    public static void replaceBeanWithProxy(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod[] methods = ctClass.getMethods();
        for (CtMethod ctMethod : methods) {
            if (!ctMethod.getName().equals(FACTORY_METHOD_NAME))
                continue;

            if (!ctClass.equals(ctMethod.getDeclaringClass())) {
                ctMethod = overrideMethod(ctClass, ctMethod);
            }
            StringBuilder methodParamTypes = new StringBuilder();
            for (CtClass type : ctMethod.getParameterTypes()) {
                methodParamTypes.append(type.getName()).append(".class").append(", ");
            }
            ctMethod.insertAfter("if(true){return org.hotswap.agent.plugin.spring.getbean.ProxyReplacer.register($0, $_,new Class[]{"
                    + methodParamTypes.substring(0, methodParamTypes.length() - 2) + "}, $args);}");
        }

    }

    /**
     * Disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
     *
     * @param ctClass
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.cglib.reflect.FastClass.Generator")
    public static void replaceSpringFastClassGenerator(CtClass ctClass) throws NotFoundException,
            CannotCompileException {
        CtConstructor[] constructors = ctClass.getConstructors();
        for (CtConstructor ctConstructor : constructors) {
            ctConstructor.insertAfter("setUseCache(false);");
        }
    }

    /**
     * Disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
     *
     * @param ctClass
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "net.sf.cglib.reflect.FastClass.Generator")
    public static void replaceCglibFastClassGenerator(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtConstructor[] constructors = ctClass.getConstructors();
        for (CtConstructor ctConstructor : constructors) {
            ctConstructor.insertAfter("setUseCache(false);");
        }
    }
}