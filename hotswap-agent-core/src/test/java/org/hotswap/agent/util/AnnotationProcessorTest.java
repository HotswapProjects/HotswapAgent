package org.hotswap.agent.util;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.handler.AnnotationProcessor;
import org.hotswap.agent.annotation.handler.InitHandler;
import org.hotswap.agent.annotation.handler.PluginAnnotation;
import org.hotswap.agent.annotation.handler.OnClassLoadedHandler;
import org.hotswap.agent.testData.SimplePlugin;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

/**
 * Created by bubnik on 11.10.13.
 */
public class AnnotationProcessorTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    PluginManager pluginManager = context.mock(PluginManager.class);
    HotswapTransformer hotswapTransformer = context.mock(HotswapTransformer.class);
    InitHandler initHandler = context.mock(InitHandler.class);
    OnClassLoadedHandler onClassLoadedHandler = context.mock(OnClassLoadedHandler.class);

    @Test
    public void testProcess() throws Exception {

        context.checking(new Expectations() {{
            allowing(pluginManager).getHotswapTransformer();
            will(returnValue(hotswapTransformer));

            // two @Init annotations in SimplePlugin
            exactly(2).of(initHandler).initMethod(with(any(PluginAnnotation.class)));
            will(returnValue(true));

            // @OnClassLoadEvent
            exactly(1).of(onClassLoadedHandler).initMethod(with(any(PluginAnnotation.class)));
            will(returnValue(true));

//         Not satisfied, because invoked via reflection
//            oneOf(simplePlugin).initPlugin();
//            oneOf(simplePlugin).initPlugin(pluginManager);
        }});

        final AnnotationProcessor annotationProcessor = new AnnotationProcessor(pluginManager);

        annotationProcessor.addAnnotationHandler(Init.class, initHandler);
        annotationProcessor.addAnnotationHandler(OnClassLoadEvent.class, onClassLoadedHandler);

        annotationProcessor.processAnnotations(new SimplePlugin());

        context.assertIsSatisfied();
    }
}
