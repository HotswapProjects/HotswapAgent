package org.hotswap.agent;

import org.hotswap.agent.annotation.handler.AnnotationProcessor;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.testData.SimplePlugin;
import org.hotswap.agent.util.scanner.ClassPathAnnotationScanner;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.Collections;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author Jiri Bubnik
 */
public class PluginManagerTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    ClassPathAnnotationScanner annotationScanner = context.mock(ClassPathAnnotationScanner.class);
    AnnotationProcessor annotationProcessor = context.mock(AnnotationProcessor.class);
    Instrumentation instrumentation = context.mock(Instrumentation.class);

    @Test
    public void testInit() throws Exception {
        PluginManager pluginManager = PluginManager.getInstance();

        context.checking(new Expectations() {{
            allowing(instrumentation).addTransformer(with(any(ClassFileTransformer.class)));
            allowing(annotationScanner).scanPlugins(with(any(ClassLoader.class)), with(any(String.class)));
            will(returnValue(Collections.singletonList(SimplePlugin.class.getName())));
            allowing(annotationProcessor).processAnnotations(with(any(Class.class)), with(any(Class.class)));
            will(returnValue(true));
        }});

        PluginRegistry pluginRegistry = pluginManager.getPluginRegistry();
        pluginRegistry.setAnnotationScanner(annotationScanner);
        pluginRegistry.setAnnotationProcessor(annotationProcessor);
        pluginManager.init(instrumentation);

        assertEquals("Plugin registered", pluginRegistry.getRegisteredPlugins().size(), 1);
        assertTrue("Plugin correct class", pluginRegistry.getRegisteredPlugins().keySet().iterator().next().equals(SimplePlugin.class));
    }


}
