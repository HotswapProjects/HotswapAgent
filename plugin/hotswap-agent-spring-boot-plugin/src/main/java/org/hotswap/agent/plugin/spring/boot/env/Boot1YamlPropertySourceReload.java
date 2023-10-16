package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.boot.core.HotswapReloadMap;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class Boot1YamlPropertySourceReload implements PropertySourceReload<Map<String, Object>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1YamlPropertySourceReload.class);
    final String name;
    final Resource resource;
    final String profile;

    Map<String, Object> hotswapMap;

    public Boot1YamlPropertySourceReload(String name, Resource resource, String profile) {
        this.name = name;
        this.resource = resource;
        this.profile = profile;
    }

    private void updateHotswapMap(Map<String, Object> newHotswapMap) {
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

    @Override
    public void reload() {
        Map<String, Object> newHotswapMap = doLoad();
        if (newHotswapMap == null || newHotswapMap.size() == 0) {
            return;
        }
        updateHotswapMap(newHotswapMap);
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
        updateHotswapMap(result);
        return hotswapMap;
    }

    private Map<String, Object> doLoad() {
        try {
            Object target = ReflectionHelper.invokeConstructor("org.springframework.boot.env.YamlPropertySourceLoader$Processor",
                    this.getClass().getClassLoader(), new Class[]{Resource.class, String.class}, resource, profile);
            return (Map<String, Object>) ReflectionHelper.invoke(target, "process");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
