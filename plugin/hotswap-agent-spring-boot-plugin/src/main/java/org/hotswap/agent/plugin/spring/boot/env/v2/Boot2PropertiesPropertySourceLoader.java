package org.hotswap.agent.plugin.spring.boot.env.v2;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;


public class Boot2PropertiesPropertySourceLoader implements PropertySourceReload<List<Map<String, ?>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2PropertiesPropertySourceLoader.class);

    private PropertiesPropertySourceLoader propertiesPropertySourceLoader;

    private ListPropertySourceReload basePropertySourceReload;


    public Boot2PropertiesPropertySourceLoader(PropertiesPropertySourceLoader propertiesPropertySourceLoader,
                                               String name, Resource resource) {
        basePropertySourceReload = new ListPropertySourceReload<>(name, resource);
        this.propertiesPropertySourceLoader = propertiesPropertySourceLoader;
    }


    @Override
    public void reload() {
        basePropertySourceReload.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public List<Map<String, ?>> load() {
        reload();
        return basePropertySourceReload.get();
    }

    /**
     * spring boot 2.x higher version
     *
     * @return
     */
    private List<Map<String, ?>> doLoad() {
        return (List<Map<String, ?>>) ReflectionHelper.invoke(propertiesPropertySourceLoader, PropertiesPropertySourceLoader.class,
                "loadProperties", new Class[]{Resource.class}, basePropertySourceReload.resource);
    }
}
