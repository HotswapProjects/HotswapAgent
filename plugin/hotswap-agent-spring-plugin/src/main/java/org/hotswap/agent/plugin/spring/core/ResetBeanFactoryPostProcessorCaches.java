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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;

import java.lang.reflect.Field;
import java.util.Set;

public class ResetBeanFactoryPostProcessorCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanFactoryPostProcessorCaches.class);

    public static void reset(DefaultListableBeanFactory beanFactory) {
        resetConfigurationClassPostProcessorCache(beanFactory);
    }

    private static void resetConfigurationClassPostProcessorCache(DefaultListableBeanFactory beanFactory) {
        LOGGER.trace("Resetting ConfigurationClassPostProcessor caches");
        int factoryId = System.identityHashCode(beanFactory);
        try {
            ConfigurationClassPostProcessor ccpp = beanFactory.getBean(ConfigurationClassPostProcessor.class);
            clearSetFieldOfConfigurationClassPostProcessor(ccpp, "factoriesPostProcessed", factoryId);
            clearSetFieldOfConfigurationClassPostProcessor(ccpp, "registriesPostProcessed", factoryId);
        } catch (NoSuchBeanDefinitionException e) {
            LOGGER.trace("ConfigurationClassPostProcessor bean doesn't present");
        }
    }

    private static void clearSetFieldOfConfigurationClassPostProcessor(ConfigurationClassPostProcessor ccpp,
                                                                       String fieldName, int factoryId) {
        try {
            Field field = ConfigurationClassPostProcessor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Set<Integer> set = (Set<Integer>) field.get(ccpp);
            set.remove(factoryId);
        } catch (Exception e) {
            LOGGER.debug("Error while resetting ConfigurationClassPostProcessor caches", e);
        }
    }
}
