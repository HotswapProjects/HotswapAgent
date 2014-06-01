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
