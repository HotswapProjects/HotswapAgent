package org.hotswap.agent.plugin.spring.api;

/**
 * @author cvictory
 * @param <T>
 */
public interface PropertySourceReloader<T> {

    void reload();

    T load();
}
