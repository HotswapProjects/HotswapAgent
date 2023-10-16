package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.core.HotswapReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Boot2YamlPropertySourceReload implements PropertySourceReload<List<Map<String, Object>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2YamlPropertySourceReload.class);

    final String name;
    final Resource resource;

    List<Map<String, Object>> hotswapMapList;

    public Boot2YamlPropertySourceReload(String name, Resource resource) {
        this.name = name;
        this.resource = resource;
    }

    private void updateHotswapMap(List<Map<String, Object>> newHotswapMapList) {
        if (hotswapMapList == null) {
            synchronized (this) {
                if (hotswapMapList == null) {
                    hotswapMapList = new ArrayList<>(newHotswapMapList.size());
                    for (Map<String, Object> map : newHotswapMapList) {
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
            Map<String, Object> hotswapMap = hotswapMapList.get(i);
            Map<String, Object> newHotswapMap = newHotswapMapList.get(i);
            updateNewValue(hotswapMap, newHotswapMap);
        }
    }

    @Override
    public void reload() {
        List<Map<String, Object>> newHotswapMapList = doLoad();
        if (newHotswapMapList == null || newHotswapMapList.size() == 0) {
            return;
        }
        updateHotswapMap(newHotswapMapList);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public List<Map<String, Object>> load() {
        List<Map<String, Object>> result = doLoad();
        if (result == null) {
            return null;
        }
        updateHotswapMap(result);
        return hotswapMapList;
    }


    protected List<Map<String, Object>> doLoad() {
        try {
            Object yamlLoader =  ReflectionHelper.invokeConstructor(
                    "org.springframework.boot.env.OriginTrackedYamlLoader",
                    this.getClass().getClassLoader(), new Class[]{Resource.class}, resource);
            return (List<Map<String, Object>>) ReflectionHelper.invoke(yamlLoader, "load");
        } catch (Exception e) {
            LOGGER.error("load yaml error, resource: {}", e, resource);
        }
        return null;
    }

    protected Map<String, Object> createMap(Map<String, Object> map) {
        return new HotswapReloadMap<>(map);
    }

    protected void updateNewValue(Map<String, Object> hotswapMap, Map<String, Object> newHotswapMap) {
        if (hotswapMap instanceof HotswapReloadMap) {
            ((HotswapReloadMap) hotswapMap).updateNewValue(newHotswapMap);
        }
    }
}
