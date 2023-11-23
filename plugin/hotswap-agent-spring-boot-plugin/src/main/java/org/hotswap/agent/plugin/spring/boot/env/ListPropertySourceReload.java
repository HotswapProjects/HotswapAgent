package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * it is not forbidden to extend this class, because there are some class conflict.
 *
 * @param <T>
 */
public class ListPropertySourceReload<T> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ListPropertySourceReload.class);

    protected final String name;
    protected final Resource resource;
    protected List<Map<String, T>> hotswapMapList;

    public ListPropertySourceReload(String name, Resource resource) {
        this.name = name;
        this.resource = resource;
    }

    protected void updateHotswapMap(List<Map<String, T>> newHotswapMapList) {
        if (hotswapMapList == null) {
            synchronized (this) {
                if (hotswapMapList == null) {
                    hotswapMapList = new ArrayList<>(newHotswapMapList.size());
                    for (Map<String, ?> map : newHotswapMapList) {
                        hotswapMapList.add(createMap(map));
                    }
                    return;
                }
            }
        }
        if (newHotswapMapList.size() != hotswapMapList.size()) {
            LOGGER.warning("hotswap map size is not equal, old size: {}, new size: {}, resource: {}", hotswapMapList.size(), newHotswapMapList.size(),
                    resource);
            return;
        }

        for (int i = 0; i < hotswapMapList.size(); i++) {
            Map<String, ?> hotswapMap = hotswapMapList.get(i);
            Map<String, ?> newHotswapMap = newHotswapMapList.get(i);
            updateNewValue(hotswapMap, newHotswapMap);
        }
    }

    Map<String, T> createMap(Map map) {
        return new HotswapSpringReloadMap<>(map);
    }

    void updateNewValue(Map hotswapMap, Map newHotswapMap) {
        if (hotswapMap instanceof HotswapSpringReloadMap) {
            ((HotswapSpringReloadMap) hotswapMap).updateNewValue(newHotswapMap);
        }
    }
}
