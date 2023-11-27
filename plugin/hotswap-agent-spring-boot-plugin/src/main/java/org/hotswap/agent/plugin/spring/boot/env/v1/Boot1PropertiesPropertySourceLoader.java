package org.hotswap.agent.plugin.spring.boot.env.v1;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.BasePropertiesPropertySourceLoader;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringProperties;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.Properties;

public class Boot1PropertiesPropertySourceLoader extends BasePropertiesPropertySourceLoader<Properties> implements PropertySourceReload<Properties> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1PropertiesPropertySourceLoader.class);
    final String name;
    final Resource resource;
    final String profile;

    public Boot1PropertiesPropertySourceLoader(String name, Resource resource, String profile) {
        super(new HotswapSpringProperties());
        this.name = name;
        this.resource = resource;
        this.profile = profile;
    }

    protected Properties doLoad() {
        try {
            Class clazz = Class.forName("org.springframework.core.io.support.PropertiesLoaderUtils");
            return (Properties) ReflectionHelper.invoke(null, clazz, "loadProperties",
                    new Class[]{Resource.class}, resource);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
