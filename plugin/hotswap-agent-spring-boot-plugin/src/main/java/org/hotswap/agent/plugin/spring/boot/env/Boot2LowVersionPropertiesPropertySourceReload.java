package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.Map;

public class Boot2LowVersionPropertiesPropertySourceReload implements PropertySourceReload<Map<String, ?>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2LowVersionPropertiesPropertySourceReload.class);

    private PropertiesPropertySourceLoader propertiesPropertySourceLoader;
    private MapPropertySourceReload mapPropertySourceReload;


    public Boot2LowVersionPropertiesPropertySourceReload(PropertiesPropertySourceLoader propertiesPropertySourceLoader,
                                                         String name, Resource resource) {
        mapPropertySourceReload = new MapPropertySourceReload(name, resource);
        this.propertiesPropertySourceLoader = propertiesPropertySourceLoader;
    }

    @Override
    public void reload() {
        Map<String, ?> newHotswapMapList = doLoad();
        if (newHotswapMapList == null || newHotswapMapList.size() == 0) {
            return;
        }
        mapPropertySourceReload.updateHotswapMap(newHotswapMapList);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Map<String, ?> load() {
        Map<String, ?> result = doLoad();
        if (result == null) {
            return null;
        }
        mapPropertySourceReload.updateHotswapMap(result);
        return mapPropertySourceReload.hotswapMap;
    }

    /**
     * spring boot 2.0 lower version
     *
     * @return
     */
    private Map<String, ?> doLoad() {
        return (Map<String, ?>) ReflectionHelper.invoke(propertiesPropertySourceLoader, PropertiesPropertySourceLoader.class,
                "loadProperties", new Class[]{Resource.class}, mapPropertySourceReload.resource);
    }
}
