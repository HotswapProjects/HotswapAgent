package org.hotswap.agent.plugin.spring.boot.env;

import java.util.function.Supplier;

public interface HotswapSpringPropertiesReloader<T> {
    default void update(Supplier<T> supplier) {
        update(supplier.get());
    }

    void update(T newValue);

    T get();
}
