package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.config.PluginManager;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

/**
 * Created by bubnik on 3.11.13.
 */
public class OnResourceWatchFileEventHandlerTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    PluginManager pluginManager = context.mock(PluginManager.class);

    @Test
    public void testInitMethod() throws Exception {

    }

    @Test
    public void testOnWatchEvent() throws Exception {

    }
}
