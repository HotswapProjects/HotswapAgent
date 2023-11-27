package org.hotswap.agent.plugin.spring.boot.env;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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

    @Override
    public void update(Map<K, V> newValue) {
        if (newValue == null || newValue.size() == 0) {
            return;
        }
        this.value = newValue;
    }

    @Override
    public Map<K, V> get() {
        return this;
    }
}
