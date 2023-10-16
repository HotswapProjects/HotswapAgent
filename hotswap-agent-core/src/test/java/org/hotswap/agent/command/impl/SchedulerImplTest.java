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
package org.hotswap.agent.command.impl;

import org.hotswap.agent.command.CommandExecutionListener;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * @author Jiri Bubnik
 */
public class SchedulerImplTest {
    Scheduler scheduler;
    ReflectionCommand command = new ReflectionCommand(new Object(), SchedulerImplTest.class.getName(), "commandMethod", getClass().getClassLoader());

    @Before
    public void setup() throws IOException {
        scheduler = new SchedulerImpl();
        scheduler.run();
    }

    @After
    public void tearDown() {
        scheduler.stop();
    }

    // method called by command - return classNameRegexp should be checked in callback listener
    @SuppressWarnings("UnusedDeclaration")
    public static boolean commandMethod() {
        return true;
    }

    @Test
    public void testScheduleCommand() throws Exception {
        final WaitHelper.ResultHolder resultHolder = new WaitHelper.ResultHolder();
        command.setCommandExecutionListener(new CommandExecutionListener() {
            @Override
            public void commandExecuted(Object result) {
                assertNotNull("Command result not null", result);
                assertTrue("Command result true", result instanceof Boolean && ((Boolean) result));
                resultHolder.result = true;
            }
        });

        scheduler.scheduleCommand(command);

        assertTrue("Event listener not called", WaitHelper.waitForResult(resultHolder));
    }
}
