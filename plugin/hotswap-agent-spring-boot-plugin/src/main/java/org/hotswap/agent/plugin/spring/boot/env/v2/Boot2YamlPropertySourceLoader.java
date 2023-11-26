package org.hotswap.agent.plugin.spring.boot.env.v2;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public class Boot2YamlPropertySourceLoader implements PropertySourceReload<List<Map<String, Object>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2YamlPropertySourceLoader.class);

    private ListPropertySourceReload basePropertySourceReload;

    public Boot2YamlPropertySourceLoader(String name, Resource resource) {
        basePropertySourceReload = new ListPropertySourceReload(name, resource);
    }

    @Override
    public void reload() {
        basePropertySourceReload.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public List<Map<String, Object>> load() {
        reload();
        return basePropertySourceReload.get();
    }


    protected List<Map<String, Object>> doLoad() {
        try {
            Object yamlLoader = ReflectionHelper.invokeConstructor(
                    "org.springframework.boot.env.OriginTrackedYamlLoader",
                    this.getClass().getClassLoader(), new Class[]{Resource.class}, basePropertySourceReload.resource);
            return (List<Map<String, Object>>) ReflectionHelper.invoke(yamlLoader, "load");
        } catch (Exception e) {
            LOGGER.error("load yaml error, resource: {}", e, basePropertySourceReload.resource);
        }
        return null;
    }
}
