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
package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.testData.SimplePlugin;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

/**
 * @author Jiri Bubnik
 */
public class InitHandlerTest {

    @Test
    public void testInitMethod() throws Exception {
        PluginManager pluginManager = PluginManager.getInstance();
        SimplePlugin simplePlugin = new SimplePlugin();

        // register the plugin
        pluginManager.getPluginRegistry().getRegisteredPlugins().put(SimplePlugin.class,
                Collections.<ClassLoader, Object>singletonMap(getClass().getClassLoader(), simplePlugin));

        InitHandler initHandler = new InitHandler(pluginManager);

        Method method = SimplePlugin.class.getMethod("initPlugin", PluginManager.class);
        PluginAnnotation<Init> pluginAnnotation = new PluginAnnotation<Init>(SimplePlugin.class,
                simplePlugin, method.getAnnotation(Init.class), method);
        assertTrue("Init successful",
                initHandler.initMethod(pluginAnnotation));
    }
}
