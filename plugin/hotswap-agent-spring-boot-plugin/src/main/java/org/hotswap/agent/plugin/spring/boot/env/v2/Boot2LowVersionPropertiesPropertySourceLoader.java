package org.hotswap.agent.plugin.spring.boot.env.v2;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.Map;

public class Boot2LowVersionPropertiesPropertySourceLoader implements PropertySourceReload<Map<String, ?>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2LowVersionPropertiesPropertySourceLoader.class);

    private PropertiesPropertySourceLoader propertiesPropertySourceLoader;
    private HotswapSpringReloadMap hotswapSpringReloadMap;
    private Resource resource;


    public Boot2LowVersionPropertiesPropertySourceLoader(PropertiesPropertySourceLoader propertiesPropertySourceLoader,
                                                         String name, Resource resource) {
        hotswapSpringReloadMap = new HotswapSpringReloadMap();
        this.propertiesPropertySourceLoader = propertiesPropertySourceLoader;
        this.resource = resource;
    }

    @Override
    public void reload() {
        hotswapSpringReloadMap.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Map<String, ?> load() {
        reload();
        return hotswapSpringReloadMap.get();
    }

    /**
     * spring boot 2.0 lower version
     *
     * @return
     */
    private Map<String, ?> doLoad() {
        return (Map<String, ?>) ReflectionHelper.invoke(propertiesPropertySourceLoader, PropertiesPropertySourceLoader.class,
                "loadProperties", new Class[]{Resource.class}, resource);
    }
}
