package org.hotswap.agent.plugin.spring.boot.env.v2;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.api.PropertySourceReload;
import org.hotswap.agent.plugin.spring.boot.env.BasePropertiesPropertySourceLoader;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;


public class Boot2PropertiesPropertySourceLoader extends BasePropertiesPropertySourceLoader<List<Map<String, ?>>> implements PropertySourceReload<List<Map<String, ?>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(Boot2PropertiesPropertySourceLoader.class);

    private PropertiesPropertySourceLoader propertiesPropertySourceLoader;
    private Resource resource;


    public Boot2PropertiesPropertySourceLoader(PropertiesPropertySourceLoader propertiesPropertySourceLoader,
                                               String name, Resource resource) {
        super(new ListPropertySourceReloader(name, resource));
        this.propertiesPropertySourceLoader = propertiesPropertySourceLoader;
        this.resource = resource;
    }

    /**
     * spring boot 2.x higher version
     *
     * @return
     */
    protected List<Map<String, ?>> doLoad() {
        return (List<Map<String, ?>>) ReflectionHelper.invoke(propertiesPropertySourceLoader, PropertiesPropertySourceLoader.class,
                "loadProperties", new Class[]{Resource.class}, resource);
    }
}
