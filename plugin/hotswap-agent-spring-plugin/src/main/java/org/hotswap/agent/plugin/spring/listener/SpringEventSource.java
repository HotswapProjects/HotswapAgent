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

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.reload.SpringBeanReload;

import java.util.ArrayList;
import java.util.List;

public class SpringEventSource {

    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringEventSource.class);
    public static final SpringEventSource INSTANCE = new SpringEventSource();

    private SpringEventSource() {
    }

    private List<SpringListener> listeners = new ArrayList<SpringListener>();

    public void addListener(SpringListener listener) {
        listeners.add(listener);
    }

    public void fireEvent(SpringEvent event) {
        for (SpringListener listener : listeners) {
            if (listener.isFilterBeanFactory() && listener.beanFactory() != null &&
                    listener.beanFactory() != event.getBeanFactory()) {
                continue;
            }
            try {
                listener.onEvent(event);
            } catch (Throwable e) {
                LOGGER.warning("SpringListener onEvent error", e);
            }
        }
    }
}
