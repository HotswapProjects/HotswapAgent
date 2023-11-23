package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.plugin.spring.env.HotswapChangeItem;

import java.util.*;

public class HotswapSpringProperties<K, V> extends Properties {

    private Properties originValue;
    private HotswapSpringMapSupport<Object, Object> mapSupport;

    public HotswapSpringProperties(Properties p) {
        super();
        this.putAll(p);
        this.originValue = p;
        mapSupport = new HotswapSpringMapSupport();
    }

    public void updateNewValue(Properties newValue) {
        this.originValue.clear();
        this.originValue.putAll(this);
        this.clear();
        this.putAll(newValue);
    }

    public Set<HotswapChangeItem> changeList() {
        return mapSupport.changeList(this, originValue);
    }
}
