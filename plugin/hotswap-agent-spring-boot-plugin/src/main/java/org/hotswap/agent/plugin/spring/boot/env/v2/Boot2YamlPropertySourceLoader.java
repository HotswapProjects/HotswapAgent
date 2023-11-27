package org.hotswap.agent.plugin.spring.boot.env.v2;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.BasePropertiesPropertySourceLoader;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public class Boot2YamlPropertySourceLoader extends BasePropertiesPropertySourceLoader<List<Map<String, Object>>> implements PropertySourceReload<List<Map<String, Object>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2YamlPropertySourceLoader.class);

    private Resource resource;

    public Boot2YamlPropertySourceLoader(String name, Resource resource) {
        super(new ListPropertySourceReloader(name, resource));
        this.resource = resource;
    }

    protected List<Map<String, Object>> doLoad() {
        try {
            Object yamlLoader = ReflectionHelper.invokeConstructor(
                    "org.springframework.boot.env.OriginTrackedYamlLoader",
                    this.getClass().getClassLoader(), new Class[]{Resource.class}, resource);
            return (List<Map<String, Object>>) ReflectionHelper.invoke(yamlLoader, "load");
        } catch (Exception e) {
            LOGGER.error("load yaml error, resource: {}", e, resource);
        }
        return null;
    }
}
