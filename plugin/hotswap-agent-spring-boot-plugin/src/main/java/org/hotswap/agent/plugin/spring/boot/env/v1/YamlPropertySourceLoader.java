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

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.boot.env.BasePropertiesPropertySourceLoader;
import org.hotswap.agent.plugin.spring.boot.env.HotswapSpringReloadMap;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.core.io.Resource;

import java.util.Map;

public class YamlPropertySourceLoader extends BasePropertiesPropertySourceLoader<Map<String, Object>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(YamlPropertySourceLoader.class);

    final String profile;
    final Resource resource;

    public YamlPropertySourceLoader(String name, Resource resource, String profile) {
        super(new HotswapSpringReloadMap());
        this.profile = profile;
        this.resource = resource;
    }

    protected Map<String, Object> doLoad() {
        try {
            Object target = ReflectionHelper.invokeConstructor("org.springframework.boot.env.YamlPropertySourceLoader$Processor",
                    this.getClass().getClassLoader(), new Class[]{Resource.class, String.class}, resource, profile);
            return (Map<String, Object>) ReflectionHelper.invoke(target, "process");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
