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
package org.hotswap.agent.plugin.spring.qualifier.early;

import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Looks up qualified beans while post processors are registered, before the configuration is frozen.
 * Spring Boot does the same for @ConfigurationPropertiesBinding converters.
 */
public class EarlyLookupPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    public static final String QUALIFIER = "earlyLookup";

    private Set<String> foundBeanNames;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        foundBeanNames = BeanFactoryAnnotationUtils.qualifiedBeansOfType((ListableBeanFactory) beanFactory,
                EarlyLookupConfiguration.QualifiedService.class, QUALIFIER).keySet();
    }

    public Set<String> getFoundBeanNames() {
        return foundBeanNames;
    }
}
