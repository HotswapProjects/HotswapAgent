package org.hotswap.agent.plugin.spring.boot.env.v1;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringProperties;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.Properties;
import java.util.function.Supplier;

public class Boot1PropertiesPropertySourceLoader implements PropertySourceReload<Properties> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1PropertiesPropertySourceLoader.class);
    final String name;
    final Resource resource;
    final String profile;

    private HotswapSpringProperties properties;

    public Boot1PropertiesPropertySourceLoader(String name, Resource resource, String profile) {
        this.name = name;
        this.resource = resource;
        this.profile = profile;
        this.properties = new HotswapSpringProperties();
    }


    @Override
    public void reload() {
        properties.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Properties load() {
        this.reload();
        return properties.get();
    }

    private Properties doLoad() {
        try {
            Class clazz = Class.forName("org.springframework.core.io.support.PropertiesLoaderUtils");
            return (Properties) ReflectionHelper.invoke(null, clazz, "loadProperties",
                    new Class[]{Resource.class}, resource);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
