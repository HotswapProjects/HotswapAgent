package org.hotswap.agent.command.impl;

import org.hotswap.agent.command.CommandExecutionListener;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
