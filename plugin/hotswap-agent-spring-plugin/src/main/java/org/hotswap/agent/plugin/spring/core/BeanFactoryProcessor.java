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
package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle;
import org.hotswap.agent.plugin.spring.transformers.api.ValueResolverSupport;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.support.*;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

public class BeanFactoryProcessor {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanFactoryProcessor.class);

    public static void removeBeanDefinition(DefaultListableBeanFactory beanFactory, String beanName) {
        resetEmbeddedValueResolvers(beanFactory, beanName);
        beanFactory.removeBeanDefinition(beanName);
    }

    public static void destroySingleton(DefaultListableBeanFactory beanFactory, String beanName) {
        // remove embeddedValueResolvers cache in PlaceholderConfigurerSupport
        resetEmbeddedValueResolvers(beanFactory, beanName);
        beanFactory.destroySingleton(beanName);
    }

    /**
     * invoked by @see org.hotswap.agent.plugin.spring.transformers.BeanFactoryTransformer
     * @param beanFactory
     * @param beanName
     */
    public static void postProcessDestroySingleton(DefaultSingletonBeanRegistry beanFactory, String beanName) {
        // check if reload , then log
        if (beanFactory instanceof ConfigurableListableBeanFactory &&
            BeanFactoryAssistant.getBeanFactoryAssistant((ConfigurableListableBeanFactory)beanFactory).isReload()) {
            LOGGER.info("destroy bean '{}' from '{}'", beanName, ObjectUtils.identityToString(beanFactory));
        }
        if (beanFactory instanceof BeanFactoryLifecycle) {
            ((BeanFactoryLifecycle)beanFactory).hotswapAgent$destroyBean(beanName);
        }
    }

    /**
     * invoked by @see org.hotswap.agent.plugin.spring.transformers.BeanFactoryTransformer
     *
     * @param beanFactory
     * @param beanName
     */
    public static void postProcessCreateBean(AbstractAutowireCapableBeanFactory beanFactory, String beanName,
        RootBeanDefinition mbd) {
        // check if reload , then log
        if (beanFactory instanceof ConfigurableListableBeanFactory
            && BeanFactoryAssistant.getBeanFactoryAssistant((ConfigurableListableBeanFactory)beanFactory).isReload()
            && mbd.isSingleton()) {
            LOGGER.info("create new singleton bean '{}' from '{}'", beanName,
                ObjectUtils.identityToString(beanFactory));
        }
    }

    public static boolean needReloadOnConstructor(DefaultListableBeanFactory beanFactory, AbstractBeanDefinition currentBeanDefinition,
                                                  String beanName, Predicate<Constructor<?>[]> predicate) {
        Constructor<?>[] constructors = determineConstructors(beanFactory, beanName);
        if (constructors != null && constructors.length > 0) {
            return predicate.test(constructors);
        }
        return false;
    }

    private static Constructor<?>[] determineConstructors(DefaultListableBeanFactory beanFactory, String beanName) {
        Class<?> beanClass = resolveBeanClass(beanFactory, beanName);
        if (beanClass == null) {
            return null;
        }
        Method method = ReflectionUtils.findMethod(beanFactory.getClass(), "determineConstructorsFromBeanPostProcessors", Class.class, String.class);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return (Constructor<?>[]) method.invoke(beanFactory, beanClass, beanName);
        } catch (Exception e) {
            LOGGER.error("determineConstructorsFromBeanPostProcessors error", e);
        }
        return null;
    }

    private static Class<?> resolveBeanClass(DefaultListableBeanFactory beanFactory, String beanName) {
        Method resolveBeanClassMethod = ReflectionUtils.findMethod(beanFactory.getClass(), "resolveBeanClass", RootBeanDefinition.class, String.class, Class[].class);
        if (resolveBeanClassMethod != null) {
            resolveBeanClassMethod.setAccessible(true);
            Class<?> beanClass = null;
            BeanDefinition rBeanDefinition = beanFactory.getMergedBeanDefinition(beanName);
            try {
                if (rBeanDefinition != null) {
                    beanClass = (Class<?>) resolveBeanClassMethod.invoke(beanFactory, rBeanDefinition, beanName, new Class[]{});
                }
                return beanClass;
            } catch (Exception e) {
                LOGGER.warning("resolveBeanClass error", e);
            }
        }
        return null;
    }

    private static void resetEmbeddedValueResolvers(DefaultListableBeanFactory beanFactory, String beanName) {
        Object target = beanFactory.getSingleton(beanName);
        if (target != null && target instanceof PlaceholderConfigurerSupport && target instanceof ValueResolverSupport) {
            ValueResolverSupport placeholderConfigurerSupport = (ValueResolverSupport) target;
            Field field = ReflectionUtils.findField(beanFactory.getClass(), "embeddedValueResolvers");
            if (field != null) {
                ReflectionUtils.makeAccessible(field);
                List<StringValueResolver> embeddedValueResolvers = ReflectionUtils.getField(field, beanFactory);
                embeddedValueResolvers.removeAll(placeholderConfigurerSupport.valueResolvers());
            }
        }
    }

    public static boolean isAllowBeanDefinitionOverriding(DefaultListableBeanFactory beanFactory) {
        // org.springframework.beans.factory.support.DefaultListableBeanFactory.isAllowBeanDefinitionOverriding is introduced in spring 4.1.2
        Object target = ReflectionHelper.getNoException(beanFactory, beanFactory.getClass(), "allowBeanDefinitionOverriding");
        if (target == null) {
            return false;
        }
        return (boolean) target;
    }

    public static void setAllowBeanDefinitionOverriding(DefaultListableBeanFactory beanFactory, boolean allowEagerClassLoading) {
        beanFactory.setAllowBeanDefinitionOverriding(allowEagerClassLoading);
    }

    public static BeanDefinition getBeanDefinition(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (beanName.startsWith("&")) {
            beanName = beanName.substring(1);
        }
        return beanFactory.getBeanDefinition(beanName);
    }

}
