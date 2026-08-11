/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class AutowiredAnnotationProcessor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AutowiredAnnotationProcessor.class);
    private static volatile Method postProcessPropertyValuesMethod;
    private static volatile boolean postProcessPropertyValuesChecked;

    public static void processSingletonBeanInjection(DefaultListableBeanFactory beanFactory) {
        try {
            Map<String, AutowiredAnnotationBeanPostProcessor> postProcessors = beanFactory.getBeansOfType(
                AutowiredAnnotationBeanPostProcessor.class);
            if (postProcessors.isEmpty()) {
                LOGGER.debug("AutowiredAnnotationProcessor not exist");
                return;
            }
            AutowiredAnnotationBeanPostProcessor postProcessor = postProcessors.values().iterator().next();
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    if (!invokePostProcessPropertyValues(postProcessor, object, beanName)) {
                        postProcessor.postProcessProperties(null, object, beanName);
                    }
                }
            }
        } catch (Throwable e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AutowiredAnnotationProcessor maybe not exist", e);
            } else {
                LOGGER.warning("AutowiredAnnotationProcessor maybe not exist : " + e.getMessage());
            }
        }
    }

    private static boolean invokePostProcessPropertyValues(AutowiredAnnotationBeanPostProcessor postProcessor,
                                                           Object bean,
                                                           String beanName) {
        Method method = getPostProcessPropertyValuesMethod(postProcessor.getClass());
        if (method == null) {
            return false;
        }
        try {
            method.invoke(postProcessor, (PropertyValues) null, (PropertyDescriptor[]) null, bean, beanName);
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Plugin error, method invocation failed", e);
        }
    }

    private static Method getPostProcessPropertyValuesMethod(Class<?> processorClass) {
        if (postProcessPropertyValuesChecked) {
            return postProcessPropertyValuesMethod;
        }
        synchronized (AutowiredAnnotationProcessor.class) {
            if (!postProcessPropertyValuesChecked) {
                try {
                    postProcessPropertyValuesMethod = processorClass.getMethod(
                            "postProcessPropertyValues",
                            PropertyValues.class,
                            PropertyDescriptor[].class,
                            Object.class,
                            String.class);
                } catch (NoSuchMethodException e) {
                    postProcessPropertyValuesMethod = null;
                }
                postProcessPropertyValuesChecked = true;
            }
        }
        return postProcessPropertyValuesMethod;
    }
}
