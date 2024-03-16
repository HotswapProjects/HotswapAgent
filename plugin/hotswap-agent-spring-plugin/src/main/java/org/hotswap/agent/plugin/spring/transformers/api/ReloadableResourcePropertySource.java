/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.transformers.api;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public interface ReloadableResourcePropertySource {

    EncodedResource encodedResource();

    Resource resource();

    default boolean reload() throws IOException {
        Properties properties = null;
        if (encodedResource() != null) {
            properties = PropertiesLoaderUtils.loadProperties(encodedResource());
        } else if (resource() != null) {
            try {
                properties = PropertiesLoaderUtils.loadProperties(resource());
            } catch (Exception e) {
                // ignore
            }
        } else {
            return false;
        }
        if (properties != null) {
            ResourcePropertySource resourcePropertySource = (ResourcePropertySource) this;
            resourcePropertySource.getSource().clear();
            resourcePropertySource.getSource().putAll((Map) properties);
        }
        return true;
    }
}
