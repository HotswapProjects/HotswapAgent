package org.hotswap.agent.plugin.spring.boot.env;

public abstract class BasePropertiesPropertySourceLoader<T> {

    protected HotswapSpringPropertiesReloader<T> properties;

    public BasePropertiesPropertySourceLoader(HotswapSpringPropertiesReloader<T> reloader) {
        this.properties = reloader;
    }

    public void reload() {
        properties.update(this::doLoad);
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */

    public T load() {
        this.reload();
        return properties.get();
    }

    /**
     * try to load properties/yaml or List, it should reuse the code of spring boot.
     * it is different in different version of spring boot , it is different in properties and yaml.
     * @return
     */
    protected abstract T doLoad();
}
