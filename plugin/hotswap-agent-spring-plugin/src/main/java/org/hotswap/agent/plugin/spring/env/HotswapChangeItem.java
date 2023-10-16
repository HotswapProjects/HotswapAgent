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
