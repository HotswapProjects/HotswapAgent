package org.hotswap.agent.config;

import org.hotswap.agent.config.PluginConfiguration;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubnik on 12.11.13.
 */
public class PluginConfigurationTest {

    @Test
    public void testGetWatchResources() throws Exception {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(getClass().getClassLoader());
        File tempFile = File.createTempFile("test", "test");

        // find by URL
        pluginConfiguration.properties.setProperty("watchResources", tempFile.toURI().toURL().toString());
        assertEquals(tempFile.toURI().toURL(), pluginConfiguration.getWatchResources()[0]);

        // find by file name
        pluginConfiguration.properties.setProperty("watchResources", tempFile.getCanonicalPath());
        assertEquals(tempFile.toURI().toURL(), pluginConfiguration.getWatchResources()[0]);
    }
}
