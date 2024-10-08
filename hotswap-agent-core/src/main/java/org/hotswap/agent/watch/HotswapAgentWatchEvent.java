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
package org.hotswap.agent.watch;

import java.nio.file.WatchEvent;

public class HotswapAgentWatchEvent<T> implements WatchEvent<T> {
    private final WatchEvent.Kind<T> kind;
    private final T context;

    private int count;

    public HotswapAgentWatchEvent(WatchEvent.Kind<T> type, T context) {
        this.kind = type;
        this.context = context;
        this.count = 1;
    }

    @Override
    public WatchEvent.Kind<T> kind() {
        return kind;
    }

    @Override
    public T context() {
        return context;
    }

    @Override
    public int count() {
        return count;
    }

    void increment() {
        count++;
    }
}
