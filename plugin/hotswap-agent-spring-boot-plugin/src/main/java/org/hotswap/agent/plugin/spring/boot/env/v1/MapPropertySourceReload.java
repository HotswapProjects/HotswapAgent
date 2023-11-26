//package org.hotswap.agent.plugin.spring.boot.env.v1;
//
//import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringReloadMap;
//import org.springframework.core.io.Resource;
//
//import java.util.Map;
//
//public class MapPropertySourceReload<T> {
//    protected final String name;
//    protected final Resource resource;
//    protected HotswapSpringReloadMap<String, T> hotswapMap;
//
//    public MapPropertySourceReload(String name, Resource resource) {
//        this.name = name;
//        this.resource = resource;
//    }
//
//    protected void updateHotswapMap(Map<String, T> newHotswapMap) {
//        if (hotswapMap == null) {
//            synchronized (this) {
//                if (hotswapMap == null) {
//                    hotswapMap = new HotswapSpringReloadMap<>(newHotswapMap);
//                    return;
//                }
//            }
//        }
//
//        if (hotswapMap instanceof HotswapSpringReloadMap) {
//            ((HotswapSpringReloadMap) hotswapMap).updateNewValue(newHotswapMap);
//        }
//    }
//}
