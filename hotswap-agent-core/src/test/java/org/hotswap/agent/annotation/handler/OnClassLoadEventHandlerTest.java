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

import static junit.framework.Assert.assertTrue;

import java.lang.reflect.Method;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.testData.SimplePlugin;
import org.hotswap.agent.util.HaClassFileTransformer;
import org.hotswap.agent.util.HotswapTransformer;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

/**
 * @author Jiri Bubnik
 */
public class OnClassLoadEventHandlerTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    PluginManager pluginManager = context.mock(PluginManager.class);
    PluginRegistry pluginRegistry = context.mock(PluginRegistry.class);
    HotswapTransformer hotswapTransformer = context.mock(HotswapTransformer.class);

    @Test
    public void testInitMethod() throws Exception {
        final ClassLoader appClassLoader = getClass().getClassLoader();

        context.checking(new Expectations() {{
            allowing(pluginManager).getHotswapTransformer(); will(returnValue(hotswapTransformer));

            allowing(pluginManager).getPluginRegistry(); will(returnValue(pluginRegistry));

            allowing(pluginRegistry).getAppClassLoader(with(any(Object.class))); will(returnValue(appClassLoader));

            oneOf(hotswapTransformer).registerTransformer(with(appClassLoader),
                    with("org.hotswap.example.type"), with(any(HaClassFileTransformer.class)));
        }});

        OnClassLoadedHandler onClassLoadedHandler = new OnClassLoadedHandler(pluginManager);

        SimplePlugin simplePlugin = new SimplePlugin();
        Method method = SimplePlugin.class.getMethod("transform");
        PluginAnnotation<OnClassLoadEvent> pluginAnnotation = new PluginAnnotation<OnClassLoadEvent>(SimplePlugin.class,
                simplePlugin, method.getAnnotation(OnClassLoadEvent.class), method);
        assertTrue("Init successful",
                onClassLoadedHandler.initMethod(pluginAnnotation));

    }

    @Test
    public void testTransform() throws Exception {

    }
}
