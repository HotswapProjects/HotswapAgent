package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;

import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;


import java.util.Map;

public class Boot1YamlPropertySourceReload implements PropertySourceReload<Map<String, Object>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1YamlPropertySourceReload.class);

    final String profile;
    MapPropertySourceReload mapPropertySourceReload;

    public Boot1YamlPropertySourceReload(String name, Resource resource, String profile) {
        mapPropertySourceReload = new MapPropertySourceReload(name, resource);
        this.profile = profile;
    }


    @Override
    public void reload() {
        Map<String, Object> newHotswapMap = doLoad();
        if (newHotswapMap == null || newHotswapMap.size() == 0) {
            return;
        }

        mapPropertySourceReload.updateHotswapMap(newHotswapMap);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Map<String, Object> load() {
        Map<String, Object> result = doLoad();
        if (result == null) {
            return result;
        }
        mapPropertySourceReload.updateHotswapMap(result);
        return mapPropertySourceReload.hotswapMap;
    }

    private Map<String, Object> doLoad() {
        try {
            Object target = ReflectionHelper.invokeConstructor("org.springframework.boot.env.YamlPropertySourceLoader$Processor",
            this.getClass().getClassLoader(), new Class[]{Resource.class, String.class}, mapPropertySourceReload.resource, profile);
            return (Map<String, Object>) ReflectionHelper.invoke(target, "process");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
