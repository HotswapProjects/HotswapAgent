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
package org.hotswap.agent.plugin.spring.boot.env;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HotswapSpringReloadMap<K, V> implements Map<K, V>, HotswapSpringPropertiesReloader<Map<K, V>> {
    private Map<K, V> value;

    public HotswapSpringReloadMap(Map<K, V> value) {
        this.value = value;
    }

    public HotswapSpringReloadMap() {
        this.value = new HashMap<>();
    }

    @Override
    public int size() {
        return this.value.size();
    }

    @Override
    public boolean isEmpty() {
        return this.value.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.value.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.value.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return this.value.get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException("put is not supported");
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("put is not supported");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("put is not supported");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("put is not supported");
    }

    @Override
    public Set<K> keySet() {
        return this.value.keySet();
    }

    @Override
    public Collection<V> values() {
        return this.value.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.value.entrySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void update(Map<K, V> newValue) {
        if (newValue == null || newValue.isEmpty()) {
            this.value = (newValue == null ? Collections.EMPTY_MAP : newValue);
            return;
        }
        this.value = newValue;
    }

    @Override
    public Map<K, V> get() {
        return this;
    }
}
