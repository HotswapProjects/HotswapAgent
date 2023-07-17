package org.hotswap.agent.plugin.spring;
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

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Field;
import java.util.List;

public class ResetBeanFactoryCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanFactoryCaches.class);

    private static final String[] POST_PROCESSORS_TO_REMOVE = new String[]{
            "org.springframework.context.annotation.ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor",
            "org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor"
    };

    public static void reset(DefaultListableBeanFactory beanFactory) {
        resetEmbeddedValueResolvers(beanFactory);
        resetBeanPostProcessors(beanFactory);
    }

    private static void resetEmbeddedValueResolvers(DefaultListableBeanFactory beanFactory) {
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
        try {
            Field field = AbstractBeanFactory.class.getDeclaredField("beanPostProcessors");
            field.setAccessible(true);
            List<BeanPostProcessor> list = (List<BeanPostProcessor>) field.get(beanFactory);
            for (BeanPostProcessor beanPostProcessor : list) {
                String className = beanPostProcessor.getClass().getName();
                for (String postProcessorToRemove : POST_PROCESSORS_TO_REMOVE) {
                    if (className.equals(postProcessorToRemove)) {
                        list.remove(beanPostProcessor);
                    }
                }
            }

            field = AbstractBeanFactory.class.getDeclaredField("beanPostProcessorCache");
            field.setAccessible(true);
            field.set(beanFactory, null);
        } catch (Throwable t) {
            LOGGER.error("Error resetting beanPostProcessors for bean factory {}", t, beanFactory);
        }
    }
}
