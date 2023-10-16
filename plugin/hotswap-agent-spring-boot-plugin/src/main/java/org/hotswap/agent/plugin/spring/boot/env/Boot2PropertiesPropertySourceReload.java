package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.core.HotswapProperties;
import org.hotswap.agent.plugin.spring.boot.core.HotswapReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Boot2PropertiesPropertySourceReload implements PropertySourceReload<List<Map<String, ?>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2PropertiesPropertySourceReload.class);

    private PropertiesPropertySourceLoader propertiesPropertySourceLoader;
    final String name;
    final Resource resource;

    List<Map<String, ?>> hotswapMapList;

    public Boot2PropertiesPropertySourceReload(PropertiesPropertySourceLoader propertiesPropertySourceLoader,
                                               String name, Resource resource) {
        this.name = name;
        this.resource = resource;
        this.propertiesPropertySourceLoader = propertiesPropertySourceLoader;
    }

    private void updateHotswapMap(List<Map<String, ?>> newHotswapMapList) {
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

    @Override
    public void reload() {
        List<Map<String, ?>> newHotswapMapList = doLoad();
        if (newHotswapMapList == null || newHotswapMapList.size() == 0) {
            return;
        }
        updateHotswapMap(newHotswapMapList);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public List<Map<String, ?>> load() {
        List<Map<String, ?>> result = doLoad();
        if (result == null) {
            return result;
        }
        updateHotswapMap(result);
        return hotswapMapList;
    }

    protected List<Map<String, ?>> doLoad() {
        return (List<Map<String, ?>>) ReflectionHelper.invoke(propertiesPropertySourceLoader, PropertiesPropertySourceLoader.class,
                "loadProperties", new Class[]{Resource.class}, resource);
    }

    protected Map createMap(Map map) {
        return new HotswapReloadMap(map);
    }

    protected void updateNewValue(Map hotswapMap, Map newHotswapMap) {
        if (hotswapMap instanceof HotswapReloadMap) {
            ((HotswapReloadMap) hotswapMap).updateNewValue(newHotswapMap);
        }
    }
}
