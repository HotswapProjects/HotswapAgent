package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.plugin.spring.env.HotswapChangeItem;

import java.util.*;

public class HotswapProperties<K, V> extends Properties {

    private Properties originValue;

    public HotswapProperties(Properties p) {
        super();
        this.putAll(p);
        this.originValue = p;
    }

    public void updateNewValue(Properties newValue) {
        this.originValue.clear();
        this.originValue.putAll(this);
        this.clear();
        this.putAll(newValue);
    }

    public Set<HotswapChangeItem> changeList() {
        Set<HotswapChangeItem> items = new HashSet<>();
        for (Map.Entry<Object, Object> entry : this.entrySet()) {
            Object key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = this.originValue.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }
        for (Map.Entry<Object, Object> entry : this.originValue.entrySet()) {
            Object key = entry.getKey();
            Object oldValue = entry.getValue();
            Object newValue = this.get(key);
            if (newValue != null && !newValue.equals(oldValue) && key instanceof String) {
                items.add(new HotswapChangeItem((String) key, oldValue, newValue));
            }
        }

        return items;
    }
}
