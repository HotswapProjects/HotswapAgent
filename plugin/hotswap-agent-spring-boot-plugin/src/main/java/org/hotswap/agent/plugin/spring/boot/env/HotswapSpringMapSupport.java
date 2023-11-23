package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.plugin.spring.env.HotswapChangeItem;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HotswapSpringMapSupport<K, V> {

    public Set<HotswapChangeItem> changeList(Map<K, V> cur, Map<K, V> originValue) {
        Set<HotswapChangeItem> items = new HashSet<>();
        for (Map.Entry<K, V> entry : cur.entrySet()) {
            Object key;
            key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = originValue.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }
        for (Map.Entry<K, V> entry : originValue.entrySet()) {
            Object key;
            key = entry.getKey();
            Object oldValue = entry.getValue();
            Object newValue = cur.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }

        return items;
    }
}
