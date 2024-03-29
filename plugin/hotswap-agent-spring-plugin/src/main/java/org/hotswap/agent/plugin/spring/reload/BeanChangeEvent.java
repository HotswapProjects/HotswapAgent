/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;


/**
 * The type Bean change event.
 */
public class BeanChangeEvent extends SpringEvent<String[]> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source      the object on which the Event initially occurred
     * @param beanFactory
     * @throws IllegalArgumentException if source is null
     */
    public BeanChangeEvent(String[] source, ConfigurableListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}
