package org.hotswap.agent.logging;

import org.hamcrest.text.StringContains;
import org.hotswap.agent.config.PluginManager;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.io.PrintStream;

/**
 * Created by bubnik on 14.10.13.
 */
public class AgentLoggerHandlerTest {
    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    PrintStream printStream = context.mock(PrintStream.class);

    @Test
    public void testHandler() {
        AgentLoggerHandler handler = new AgentLoggerHandler();
        handler.setPrintStream(printStream);

        context.checking(new Expectations() {{
            oneOf(printStream).println(with(new StringContains("DEBUG (org.hotswap.agent.config.PluginManager) - A 1 B 2 C 3")));
        }});

        handler.print(PluginManager.class, AgentLogger.Level.DEBUG, "A {} B {} C {}", null, "1", 2, 3L);
    }

}
