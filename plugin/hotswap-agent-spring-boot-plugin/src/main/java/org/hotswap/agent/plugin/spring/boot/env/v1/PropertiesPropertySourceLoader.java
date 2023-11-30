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
package org.hotswap.agent.plugin.spring.boot.env.v1;

import java.util.Properties;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.boot.env.BasePropertiesPropertySourceLoader;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringProperties;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

public class PropertiesPropertySourceLoader extends BasePropertiesPropertySourceLoader<Properties> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertiesPropertySourceLoader.class);
    final String name;
    final Resource resource;
    final String profile;

    public PropertiesPropertySourceLoader(String name, Resource resource, String profile) {
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
