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
