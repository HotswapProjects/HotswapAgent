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
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResetBeanFactoryCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanFactoryCaches.class);

    public static void reset(DefaultListableBeanFactory beanFactory) {
        // fixme 为什么要清空EmbeddedValueResolvers
//        resetEmbeddedValueResolvers(beanFactory);
        // fixme 为什么要清空BeanPostProcessors
//        resetBeanPostProcessors(beanFactory);
//        resetBeanFactoryPostProcessors(beanFactory);
    }

    public static void resetEmbeddedValueResolvers(DefaultListableBeanFactory beanFactory) {
        try {
            Field field = AbstractBeanFactory.class.getDeclaredField("embeddedValueResolvers");
            field.setAccessible(true);
            List<StringValueResolver> embeddedValueResolvers = (List<StringValueResolver>) field.get(beanFactory);
            if (embeddedValueResolvers != null && !embeddedValueResolvers.isEmpty()) {
                embeddedValueResolvers.clear();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Error resetting embeddedValueResolvers for bean factory {}", e, beanFactory);
        }
    }

    private static void resetBeanPostProcessors(DefaultListableBeanFactory beanFactory) {
        resetBeanPostProcessorList(beanFactory);
        resetBeanPostProcessorCache(beanFactory);
    }

    private static void resetBeanPostProcessorCache(DefaultListableBeanFactory beanFactory) {
        try {
            Field field = AbstractBeanFactory.class.getDeclaredField("beanPostProcessorCache");
            field.setAccessible(true);
            field.set(beanFactory, null);
        } catch (Throwable t) {
            LOGGER.error("Error resetting beanPostProcessors for bean factory {}", t, beanFactory);
        }
    }

    private static void resetBeanPostProcessorList(DefaultListableBeanFactory beanFactory) {
        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);
        Set<String> postProcessorClasses = new HashSet<>();
        for (String postProcessorName : postProcessorNames) {
            try {
                BeanPostProcessor postProcessor = beanFactory.getBean(postProcessorName, BeanPostProcessor.class);
                postProcessorClasses.add(postProcessor.getClass().getName());
                BeanFactoryProcessor.removeBeanDefinition(beanFactory, postProcessorName);
                LOGGER.debug("Removed bean definition for beanPostProcessor {}", postProcessorName);
            } catch (Throwable t) {
                LOGGER.debug("Error remove beanPostProcessor {}", t, postProcessorName);
            }
        }

        try {
            Field field = AbstractBeanFactory.class.getDeclaredField("beanPostProcessors");
            field.setAccessible(true);
            List<BeanPostProcessor> list = (List<BeanPostProcessor>) field.get(beanFactory);
            for (BeanPostProcessor beanPostProcessor : list) {
                String className = beanPostProcessor.getClass().getName();
                if (postProcessorClasses.contains(className)) {
                    list.remove(beanPostProcessor);
                    LOGGER.debug("Removed beanPostProcessor {}", className);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Error resetting beanPostProcessors", t);
        }
    }

    private static void resetBeanFactoryPostProcessors(DefaultListableBeanFactory factory) {
        String[] names = factory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);
        LOGGER.debug("Remove all BeanFactoryPostProcessor {}", Arrays.toString(names));
        for (String name : names) {
            try {
                BeanFactoryProcessor.removeBeanDefinition(factory, name);
            } catch (Throwable t) {
                LOGGER.debug("Fail to remove BeanFactoryPostProcessor {}", name);
            }
        }
    }
}
