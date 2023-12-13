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

import java.util.EventListener;

/**
 * Spring event.
 *
 * @param <E> the event type
 */
public interface SpringListener<E extends SpringEvent<?>> extends EventListener {

    DefaultListableBeanFactory beanFactory();

    /**
     * Handle the event.
     *
     * @param event the event to respond to
     */
    void onEvent(E event);

    default boolean shouldSkip(E event) {
        return !isParentOrSelf(beanFactory(), event.getBeanFactory());
    }

    static boolean isParentOrSelf(ConfigurableListableBeanFactory beanFactory,
        ConfigurableListableBeanFactory sourceBeanFactory) {
        if (beanFactory == null) {
            return false;
        }
        if (sourceBeanFactory == beanFactory) {
            return true;
        }
        if (sourceBeanFactory.getParentBeanFactory() == null) {
            return false;
        }
        if (sourceBeanFactory.getParentBeanFactory() instanceof ConfigurableListableBeanFactory) {
            return isParentOrSelf(beanFactory, (ConfigurableListableBeanFactory)
                sourceBeanFactory.getParentBeanFactory());
        }
        return false;
    }

    default int priority() {
        return 10000;
    }

}
