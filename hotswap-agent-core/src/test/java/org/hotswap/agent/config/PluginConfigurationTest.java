/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.config;

import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for configuration.
 *
 * @author Jiri Bubnik
 */
public class PluginConfigurationTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    static class ExtClassLoader extends ClassLoader implements HotswapAgentClassLoaderExt {
        URL[] extraClassPath;

        ExtClassLoader() {
            super(null);
        }

        @Override
        public void $$ha$setExtraClassPath(URL[] extraClassPath) {
            this.extraClassPath = extraClassPath;
        }

        @Override
        public void $$ha$setWatchResourceLoader(WatchResourcesClassLoader watchResourceLoader) {
        }
    }

    @Test
    public void extraClasspathAppliedToHotswapAgentClassLoaderExt() throws Exception {
        File extra = folder.newFolder("extra");
        ExtClassLoader classLoader = new ExtClassLoader();
        PluginConfiguration pluginConfiguration = initWithExtraClasspath(classLoader, extra);

        assertTrue(pluginConfiguration.isExtraClasspathApplied());
        assertArrayEquals(new URL[]{extra.getCanonicalFile().toURI().toURL()}, classLoader.extraClassPath);
    }

    @Test
    public void extraClasspathNotAppliedToClassLoaderWithoutUcp() throws Exception {
        File extra = folder.newFolder("extra");
        PluginConfiguration pluginConfiguration = initWithExtraClasspath(new ClassLoader(null) {}, extra);

        assertFalse(pluginConfiguration.isExtraClasspathApplied());
    }

    private static PluginConfiguration initWithExtraClasspath(ClassLoader classLoader, File extra) {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(null, classLoader, false);
        pluginConfiguration.properties.setProperty("extraClasspath", extra.getAbsolutePath());
        pluginConfiguration.init();
        return pluginConfiguration;
    }

    @Test
    public void testGetWatchResources() throws Exception {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(getClass().getClassLoader());
        File tempFile = File.createTempFile("test", "test");

        // find by URL
        pluginConfiguration.properties.setProperty("watchResources", tempFile.toURI().toURL().toString());
        assertEquals(tempFile.toURI().toURL(), pluginConfiguration.getWatchResources()[0]);

        // find by file name
        pluginConfiguration.properties.setProperty("watchResources", tempFile.getAbsolutePath());

        // On Mac OS X, 10.9.4, the temp folders use a path like "/var/..." and the canonical path is like "/private/var/..."
        // the getWatchResources() uses a getCanonicalFile() internally, so it returns "/private/var/...", so using
        // the cananicalFile as the expectation in the assertEquals to let this test succeed.
        // Instead, could also change getWatchResources() to use getAbsouluteFile() instead of getCanonicalFile()?
        File canonicalFile = tempFile.getCanonicalFile();
        assertEquals(canonicalFile.toURI().toURL(), pluginConfiguration.getWatchResources()[0]);
    }
}
