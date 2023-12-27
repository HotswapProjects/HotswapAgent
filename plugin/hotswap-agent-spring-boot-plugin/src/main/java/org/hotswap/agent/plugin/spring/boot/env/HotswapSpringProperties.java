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

import java.util.Properties;

public class HotswapSpringProperties extends Properties implements HotswapSpringPropertiesReloader<Properties> {

    public HotswapSpringProperties() {
        super();
    }

    public HotswapSpringProperties(Properties properties) {
        super();
        this.putAll(properties);
    }


    @Override
    public void update(Properties newValue) {
        if (newValue == null || newValue.isEmpty()) {
            this.clear();
            return;
        }
        this.clear();
        this.putAll(newValue);
    }

    @Override
    public Properties get() {
        return this;
    }
}
