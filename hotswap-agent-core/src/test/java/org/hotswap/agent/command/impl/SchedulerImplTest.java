/*
 * Copyright 2013-2026 the HotswapAgent authors.
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

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.CommandExecutionListener;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Jiri Bubnik
 */
public class SchedulerImplTest {
    Scheduler scheduler;
    ReflectionCommand command = new ReflectionCommand(new Object(), SchedulerImplTest.class.getName(), "commandMethod", getClass().getClassLoader());

    @Before
    public void setup() throws IOException {
        scheduler = new SchedulerImpl(false);
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

    @Test
    public void commandWaitsForTheEndOfRedefinition() throws Exception {
        final WaitHelper.ResultHolder resultHolder = new WaitHelper.ResultHolder();
        command.setCommandExecutionListener(result -> resultHolder.result = true);

        scheduler.onRedefinitionStepStarted();
        scheduler.scheduleCommandOnClassesRedefinedOrTimeout(command, 0);
        Thread.sleep(500);
        assertFalse("Command executed during the redefinition", resultHolder.result);

        scheduler.onRedefinitionStepFinished();
        assertTrue("Command not executed after the redefinition", WaitHelper.waitForResult(resultHolder));
    }

    @Test
    public void commandWaitsForTheRedefinitionReportedByJvm() throws Exception {
        scheduler.stop();
        scheduler = new SchedulerImpl(true);
        scheduler.run();
        scheduler.onClassesRedefined();
        final WaitHelper.ResultHolder resultHolder = new WaitHelper.ResultHolder();
        command.setCommandExecutionListener(result -> resultHolder.result = true);

        scheduler.onRedefinitionStepStarted();
        scheduler.scheduleCommandOnClassesRedefinedOrTimeout(command, 0);
        scheduler.onRedefinitionStepFinished();
        Thread.sleep(500);
        assertFalse("Command executed during the redefinition", resultHolder.result);

        scheduler.onClassesRedefined();
        assertTrue("Command not executed after the redefinition", WaitHelper.waitForResult(resultHolder));
    }

    @Test
    public void commandThreadsAreLimited() throws Exception {
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        final AtomicInteger executed = new AtomicInteger();
        scheduler.setMaxCommandThreads(2);
        for (int i = 0; i < 6; i++) {
            scheduler.scheduleCommand(new Command() {
                @Override
                public void executeCommand() {
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running.decrementAndGet();
                    executed.incrementAndGet();
                }
            }, 0);
        }

        assertTrue("Not all commands executed", WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() {
                return executed.get() == 6;
            }
        }, 5000));
        assertEquals(2, maxRunning.get());
    }

    @Test
    public void waitingCommandsAreExecutedInTheOrderOfTheirTime() throws Exception {
        final List<Integer> executed = Collections.synchronizedList(new ArrayList<Integer>());
        scheduler.setMaxCommandThreads(1);
        // holds the only thread until the other commands are due
        scheduler.scheduleCommand(new Command() {
            @Override
            public void executeCommand() {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, 0);
        Thread.sleep(200);
        for (final int timeout : new int[] {300, 200, 100, 0}) {
            scheduler.scheduleCommand(new Command() {
                @Override
                public void executeCommand() {
                    executed.add(timeout);
                }
            }, timeout);
        }

        assertTrue("Not all commands executed", WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() {
                return executed.size() == 4;
            }
        }, 5000));
        assertEquals(Arrays.asList(0, 100, 200, 300), executed);
    }

    @Test
    public void commandIsExecutedAfterItsThreadFailedToStart() throws Exception {
        final AtomicInteger failures = new AtomicInteger(2);
        scheduler.stop();
        scheduler = new SchedulerImpl(false) {
            @Override
            void start(CommandExecutor executor) {
                if (failures.getAndDecrement() > 0) {
                    throw new OutOfMemoryError("unable to create native thread");
                }
                super.start(executor);
            }
        };
        scheduler.run();
        final WaitHelper.ResultHolder resultHolder = new WaitHelper.ResultHolder();
        command.setCommandExecutionListener(result -> resultHolder.result = true);

        scheduler.scheduleCommand(command, 0);

        assertTrue("Command not executed", WaitHelper.waitForResult(resultHolder, 5000));
        assertTrue("The thread did not fail to start", failures.get() < 0);
    }
}
