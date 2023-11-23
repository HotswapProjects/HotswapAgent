package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.Properties;

public class Boot1PropertiesPropertySourceReload implements PropertySourceReload<Properties> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot1PropertiesPropertySourceReload.class);
    final String name;
    final Resource resource;
    final String profile;

    Properties properties;

    public Boot1PropertiesPropertySourceReload(String name, Resource resource, String profile) {
        this.name = name;
        this.resource = resource;
        this.profile = profile;
    }


    private void updateHotswap(Properties newProperties) {
        if (properties == null) {
            synchronized (this) {
                if (properties == null) {
                    properties = new HotswapSpringProperties<>(newProperties);
                    return;
                }
            }
        }
        if (properties instanceof HotswapSpringProperties) {
            ((HotswapSpringProperties) properties).updateNewValue(newProperties);
        }
    }

    @Override
    public void reload() {
        Properties newHotswapMap = doLoad();
        if (newHotswapMap == null || newHotswapMap.size() == 0) {
            return;
        }
        updateHotswap(newHotswapMap);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */
    @Override
    public Properties load() {
        Properties result = doLoad();
        if (result == null) {
            return null;
        }
        updateHotswap(result);

        return properties;
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
