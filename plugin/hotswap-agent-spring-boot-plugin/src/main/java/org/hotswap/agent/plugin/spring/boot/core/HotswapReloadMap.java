package org.hotswap.agent.plugin.spring.boot.core;

import org.hotswap.agent.plugin.spring.env.HotswapChangeItem;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HotswapReloadMap<K, V> implements Map<K, V> {
    private Map<K, V> value;
    private Map<K, V> originValue;

    public HotswapReloadMap(Map<K, V> value) {
        this.value = value;
        this.originValue = value;
    }

    public void updateNewValue(Map<K, V> newValue) {
        this.originValue = this.value;
        this.value = newValue;
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

    public Set<HotswapChangeItem> changeList() {
        Set<HotswapChangeItem> items = new HashSet<>();
        for (Map.Entry<K, V> entry : this.entrySet()) {
            Object key;
            key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = this.originValue.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }
        for (Map.Entry<K, V> entry : this.originValue.entrySet()) {
            Object key;
            key = entry.getKey();
            Object oldValue = entry.getValue();
            Object newValue = this.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }

        return items;
    }
}
