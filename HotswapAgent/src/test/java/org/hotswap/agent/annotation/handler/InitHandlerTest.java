package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.testData.SimplePlugin;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

/**
 * Created by bubnik on 11.10.13.
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
        PluginAnnotation<Init> pluginAnnotation = new PluginAnnotation<Init>(
                simplePlugin, method.getAnnotation(Init.class), method);
        assertTrue("Init successful",
                initHandler.initMethod(pluginAnnotation));
    }
}
