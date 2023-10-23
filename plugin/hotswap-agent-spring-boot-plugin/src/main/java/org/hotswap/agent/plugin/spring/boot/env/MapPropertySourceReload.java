package org.hotswap.agent.plugin.spring.boot.env;

import org.springframework.core.io.Resource;

import java.util.Map;

public class MapPropertySourceReload<T> {
    protected final String name;
    protected final Resource resource;
    protected Map<String, T> hotswapMap;

    public MapPropertySourceReload(String name, Resource resource) {
        this.name = name;
        this.resource = resource;
    }

    protected void updateHotswapMap(Map<String, T> newHotswapMap) {
        if (hotswapMap == null) {
            synchronized (this) {
                if (hotswapMap == null) {
                    hotswapMap = new HotswapReloadMap<>(newHotswapMap);
                    return;
                }
            }
        }

        if (hotswapMap instanceof HotswapReloadMap) {
            ((HotswapReloadMap) hotswapMap).updateNewValue(newHotswapMap);
        }
    }
}