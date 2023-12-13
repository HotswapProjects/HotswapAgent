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

import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;

public class SpringEventSource {

    private final static AgentLogger LOGGER = AgentLogger.getLogger(SpringEventSource.class);
    public static final SpringEventSource INSTANCE = new SpringEventSource();

    private SpringEventSource() {
    }

    private final Set<SpringListener<SpringEvent<?>>> listeners = new HashSet<>();

    public void addListener(SpringListener<SpringEvent<?>> listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            if (listeners.contains(listener)) {
                LOGGER.debug("SpringListener already registered, {}", listener);
                return;
            }
            listeners.add(listener);
        }
    }

    public void fireEvent(SpringEvent<?> event) {
        for (SpringListener<SpringEvent<?>> listener : listeners) {
            if (listener.shouldSkip(event)) {
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
