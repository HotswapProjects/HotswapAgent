package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginManager;
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
 * Created by bubnik on 12.10.13.
 */
public class TransformHandlerTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    PluginManager pluginManager = context.mock(PluginManager.class);
    HotswapTransformer hotswapTransformer = context.mock(HotswapTransformer.class);

    @Test
    public void testInitMethod() throws Exception {
        context.checking(new Expectations() {{
            allowing(pluginManager).getHotswapTransformer(); will(returnValue(hotswapTransformer));

            oneOf(hotswapTransformer).registerTransformer(with("org.hotswap.example.type"), with(any(ClassFileTransformer.class)));
        }});

        TransformHandler transformHandler = new TransformHandler(pluginManager);

        SimplePlugin simplePlugin = new SimplePlugin();
        Method method = SimplePlugin.class.getMethod("transform");
        PluginAnnotation<Transform> pluginAnnotation = new PluginAnnotation<Transform>(
                simplePlugin, method.getAnnotation(Transform.class), method);
        assertTrue("Init successful",
                transformHandler.initMethod(pluginAnnotation));

    }

    @Test
    public void testTransform() throws Exception {

    }
}
