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

import static org.junit.Assert.assertFalse;

import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.junit.Test;

public class PluginRegistryTest {

    @Test
    public void doesNotLoadDisabledPlugin() {
        HotswapAgent.parseArgs("disablePlugin=DisabledTestPlugin");
        ClassLoader classLoader = getClass().getClassLoader();
        PluginRegistry pluginRegistry = new PluginRegistry(PluginManager.getInstance(), null);

        pluginRegistry.scanPlugins(classLoader, "org.hotswap.agent.config.disabledplugin");

        assertFalse(ClassLoaderHelper.isClassLoaded(classLoader, "org.hotswap.agent.config.disabledplugin.DisabledPlugin"));
    }
}
