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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;

import java.lang.reflect.Field;
import java.util.Set;

public class ResetBeanFactoryPostProcessorCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanFactoryPostProcessorCaches.class);

    public static void reset(DefaultListableBeanFactory beanFactory) {
        resetConfigurationClassPostProcessorCache(beanFactory);
    }

    public static void resetConfigurationClassPostProcessorCache(DefaultListableBeanFactory beanFactory) {
        try {
            int factoryId = System.identityHashCode(beanFactory);
            ConfigurationClassPostProcessor ccpp = beanFactory.getBean(ConfigurationClassPostProcessor.class);
            Field field = ConfigurationClassPostProcessor.class.getDeclaredField("factoriesPostProcessed");
            field.setAccessible(true);
            Set<Integer> factoriesPostProcessed = (Set<Integer>) field.get(ccpp);
            factoriesPostProcessed.remove(factoryId);

            field = ConfigurationClassPostProcessor.class.getDeclaredField("registriesPostProcessed");
            field.setAccessible(true);
            Set<Integer> registriesPostProcessed = (Set<Integer>) field.get(ccpp);
            registriesPostProcessed.remove(factoryId);
        } catch (Exception e) {
            LOGGER.debug("ConfigurationClassPostProcessor bean doesn't present");
        }
    }
}
