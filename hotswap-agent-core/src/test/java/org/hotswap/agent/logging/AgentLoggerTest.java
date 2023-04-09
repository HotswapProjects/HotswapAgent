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

import org.hotswap.agent.testData.SimplePlugin;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

/**
 * Created by bubnik on 12.10.13.
 */
public class AgentLoggerTest {
    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    AgentLoggerHandler handler = context.mock(AgentLoggerHandler.class);


    @Test
    public void testDefaultLevel() throws Exception {
        final Class clazz = SimplePlugin.class;
        final String message = "Test";
        final Throwable error = new Throwable();
        AgentLogger.setHandler(handler);

        context.checking(new Expectations() {{
            oneOf(handler).print(clazz, AgentLogger.Level.ERROR, message, null);
            oneOf(handler).print(clazz, AgentLogger.Level.INFO, message, error);
            never(handler).print(clazz, AgentLogger.Level.TRACE, message, error);
        }});

        // default level INFO
        AgentLogger logger = AgentLogger.getLogger(clazz);
        logger.error(message);
        logger.info(message, error);
        logger.trace(message, error);

        // return default handler for other tests
        AgentLogger.setHandler(new AgentLoggerHandler());

        context.assertIsSatisfied();
    }
}
