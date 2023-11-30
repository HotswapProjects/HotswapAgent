/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.plugin.spring.api.PropertySourceReloader;

/**
 * BasePropertiesPropertySourceLoader: load properties/yaml or List, it should reuse the code of spring boot.
 * @param <T>
 */
public abstract class BasePropertiesPropertySourceLoader<T> implements PropertySourceReloader<T> {

    protected HotswapSpringPropertiesReloader<T> properties;

    public BasePropertiesPropertySourceLoader(HotswapSpringPropertiesReloader<T> reloader) {
        this.properties = reloader;
    }

    /**
     * reload properties/yaml or List, it will invoke doLoad() to load properties/yaml or List.
     */
    public final void reload() {
        properties.update(this::doLoad);
    }

    /**
     * load properties/yaml or List: it should invoke reload() to reload properties/yaml or List.
     */
    public final T load() {
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
