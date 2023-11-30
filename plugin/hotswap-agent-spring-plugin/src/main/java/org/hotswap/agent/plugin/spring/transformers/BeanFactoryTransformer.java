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
import org.hotswap.agent.plugin.spring.core.BeanFactoryProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Transformer for Spring BeanFactory hierarchy.
 */
public class BeanFactoryTransformer {

    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry")
    public static void registerDefaultSingletonBeanRegistry(ClassLoader appClassLoader, CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        clazz.addField(CtField.make("private java.util.Set hotswapAgent$destroyBean = new java.util.HashSet();", clazz));
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle"));
        clazz.addMethod(CtNewMethod.make("public boolean hotswapAgent$isDestroyedBean(String beanName) { return hotswapAgent$destroyBean.contains(beanName); }", clazz));
        clazz.addMethod(CtNewMethod.make("public void hotswapAgent$destroyBean(String beanName) { hotswapAgent$destroyBean.add(beanName); }", clazz));
        clazz.addMethod(CtNewMethod.make("public void hotswapAgent$clearDestroyBean() { hotswapAgent$destroyBean.clear(); }", clazz));

        CtMethod destroySingletonMethod = clazz.getDeclaredMethod("destroySingleton", new CtClass[]{classPool.get(String.class.getName())});
        destroySingletonMethod.insertAfter(BeanFactoryProcessor.class.getName() + ".postProcessDestroySingleton($0, $1);");
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory")
    public static void registerAbstractAutowireCapableBeanFactory(ClassLoader appClassLoader, CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtMethod createBeanMethod = clazz.getDeclaredMethod("createBean", new CtClass[]{
                classPool.get(String.class.getName()),classPool.get("org.springframework.beans.factory.support.RootBeanDefinition"),
                classPool.get(Object[].class.getName())});
        createBeanMethod.insertAfter(BeanFactoryProcessor.class.getName() + ".postProcessCreateBean($0, $1, $2);");

    }
}
