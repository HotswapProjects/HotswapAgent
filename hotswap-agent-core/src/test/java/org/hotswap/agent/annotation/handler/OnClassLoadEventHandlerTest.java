package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.testData.SimplePlugin;
import org.hotswap.agent.util.HotswapTransformer;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;

import static junit.framework.Assert.assertTrue;

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
                    with("org.hotswap.example.type"), with(any(ClassFileTransformer.class)));
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
