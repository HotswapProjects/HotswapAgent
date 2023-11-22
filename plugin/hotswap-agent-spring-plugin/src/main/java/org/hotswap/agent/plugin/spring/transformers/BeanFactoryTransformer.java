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
