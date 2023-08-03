package org.hotswap.agent.plugin.spring.transformers.api;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public interface IResourcePropertySource {

    EncodedResource encodedResource();

    Resource resource();

    ResourcePropertySource resourcePropertySource();

    default boolean reloadPropertySource() throws IOException {
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
            resourcePropertySource().getSource().clear();
            resourcePropertySource().getSource().putAll((Map) properties);
        }
        return true;
    }
}
