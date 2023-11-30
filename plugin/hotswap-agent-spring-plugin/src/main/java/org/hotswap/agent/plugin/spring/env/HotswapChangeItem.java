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
package org.hotswap.agent.plugin.spring.env;

import java.util.Objects;

public class HotswapChangeItem {
    private String key;
    private Object oldValue;
    private Object newValue;

    public HotswapChangeItem(String key, Object oldValue, Object newValue) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getKey() {
        return key;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HotswapChangeItem)) return false;
        HotswapChangeItem that = (HotswapChangeItem) o;
        return Objects.equals(getKey(), that.getKey()) && Objects.equals(getOldValue(), that.getOldValue()) && Objects.equals(getNewValue(), that.getNewValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getOldValue(), getNewValue());
    }
}
