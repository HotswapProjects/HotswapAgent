package org.hotswap.agent.plugin.spring.boot.env;

public abstract class BasePropertiesPropertySourceLoader<T> {

    protected HotswapSpringPropertiesReloader<T> properties;

    public BasePropertiesPropertySourceLoader(HotswapSpringPropertiesReloader<T> reloader) {
        this.properties = reloader;
    }

    public void reload() {
        properties.update(() -> doLoad());
    }

    /**
     * >= spring boot 2.0.0, it will call load1
     */

    public T load() {
        this.reload();
        return properties.get();
    }

    protected abstract T doLoad();
}
