package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginManager;
import org.hotswap.agent.PluginRegistry;
import org.hotswap.agent.annotation.Transform;
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
public class TransformHandlerTest {

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

        TransformHandler transformHandler = new TransformHandler(pluginManager);

        SimplePlugin simplePlugin = new SimplePlugin();
        Method method = SimplePlugin.class.getMethod("transform");
        PluginAnnotation<Transform> pluginAnnotation = new PluginAnnotation<Transform>(SimplePlugin.class,
                simplePlugin, method.getAnnotation(Transform.class), method);
        assertTrue("Init successful",
                transformHandler.initMethod(pluginAnnotation));

    }

    @Test
    public void testTransform() throws Exception {

    }
}
