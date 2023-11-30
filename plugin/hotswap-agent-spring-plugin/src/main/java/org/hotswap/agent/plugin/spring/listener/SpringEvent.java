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
package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.EventObject;

/**
 * Spring event.
 * It is used to notify the plugin that a bean definition has changed.
 * Spring boot and Spring should send some events each other, the event and listener are used to satisfy this requirement.
 *
 * @param <T> the event type
 */
public abstract class SpringEvent<T> extends EventObject {

    private ConfigurableListableBeanFactory beanFactory;

    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public SpringEvent(T source, ConfigurableListableBeanFactory beanFactory) {
        super(source);
        this.beanFactory = beanFactory;
    }

    public T getSource() {
        return (T) super.getSource();
    }

    public ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }
}
