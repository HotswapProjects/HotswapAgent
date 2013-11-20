package org.hotswap.agent.command.impl;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.CommandExecutionListener;
import org.hotswap.agent.command.Scheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Created by bubnik on 2.11.13.
 */
public class SchedulerImplTest {
    Scheduler scheduler;
    Command command = new Command(new Object(), SchedulerImplTest.class.getName(), "commandMethod", getClass().getClassLoader());

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
    public boolean commandMethod() {
        return true;
    }

    @Test
    public void testScheduleCommand() throws Exception {
        final ResultHolder resultHolder = new ResultHolder();
        command.setCommandExecutionListener(new CommandExecutionListener() {
            @Override
            public void commandExecuted(Object result) {
                assertNotNull("Command result not null", result);
                assertTrue("Command result true", result instanceof Boolean && ((Boolean) result));
                resultHolder.result = true;
            }
        });

        scheduler.scheduleCommand(command);

        assertTrue("Event listener not called", waitForResult(resultHolder));
    }

    // each 10 ms check if result is true, max 1000 ms
    private boolean waitForResult(ResultHolder resultHolder) {
        for (int i = 0; i < 100; i++) {
            if (resultHolder.result)
                return true;

            // wait for command to execute
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        return false;
    }

    private static class ResultHolder {
        boolean result = false;
    }
}
