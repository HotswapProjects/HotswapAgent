package org.hotswap.agent.plugin.spring.api;

public interface PropertySourceReload<T> {

    void reload();

    T load();
}
