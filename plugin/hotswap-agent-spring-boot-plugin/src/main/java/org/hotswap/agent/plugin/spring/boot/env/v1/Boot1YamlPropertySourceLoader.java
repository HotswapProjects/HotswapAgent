package org.hotswap.agent.plugin.spring.boot.env.v1;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.Map;

public class Boot1YamlPropertySourceLoader implements PropertySourceReload<Map<String, Object>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1YamlPropertySourceLoader.class);

    final String profile;
    final Resource resource;
    HotswapSpringReloadMap<String, Object> mapPropertySourceReloadMap;

    public Boot1YamlPropertySourceLoader(String name, Resource resource, String profile) {
        mapPropertySourceReloadMap = new HotswapSpringReloadMap();
        this.profile = profile;
        this.resource = resource;
    }


    @Override
    public void reload() {
        mapPropertySourceReloadMap.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Map<String, Object> load() {
        reload();
        return mapPropertySourceReloadMap.get();
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
