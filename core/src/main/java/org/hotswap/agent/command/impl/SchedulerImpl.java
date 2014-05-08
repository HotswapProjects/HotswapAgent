package org.hotswap.agent.command.impl;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;

import java.util.*;

/**
 * Default command scheduler implementation.
 *
 * @author Jiri Bubnik
 */
public class SchedulerImpl implements Scheduler {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SchedulerImpl.class);

    int DEFAULT_SCHEDULING_TIMEOUT = 100;

    final Map<Command, Long> scheduledCommands = Collections.synchronizedMap(new HashMap<Command, Long>());
    final Set<Command> runningCommands = Collections.synchronizedSet(new HashSet<Command>());

    Thread runner;
    boolean stopped;

    @Override
    public void scheduleCommand(Command command) {
        scheduleCommand(command, DEFAULT_SCHEDULING_TIMEOUT);
    }

    @Override
    public void scheduleCommand(Command command, int timeout) {
        synchronized (scheduledCommands) {
            Command targetCommand = command;
            if (scheduledCommands.containsKey(command) && (command instanceof MergeableCommand)) {
                // get existing equals command and merge it
                for (Command scheduledCommand : scheduledCommands.keySet()) {
                    if (command.equals(scheduledCommand)) {
                        targetCommand = ((MergeableCommand) command).merge(scheduledCommand);
                        break;
                    }
                }
            }

            // map may already contain equals command, put will replace it and reset timer
            scheduledCommands.put(targetCommand, System.currentTimeMillis() + timeout);
            LOGGER.trace("{} scheduled for execution in {}ms", targetCommand, timeout);
        }
    }

    /**
     * One cycle of the scheduler agent. Process all commands which are not currently
     * running and time lower than current milliseconds.
     *
     * @return true if the agent should continue (false for fatal error)
     */
    private boolean processCommands() {
        Long currentTime = System.currentTimeMillis();
        synchronized (scheduledCommands) {
            for (Iterator<Map.Entry<Command, Long>> it = scheduledCommands.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Command, Long> entry = it.next();
                Command command = entry.getKey();

                // if timeout and command is not running
                if (entry.getValue() < currentTime && !runningCommands.contains(command)) {
                    // execute in separate thread
                    executeCommand(command);
                    // remove from scheduled commands
                    it.remove();
                }
            }
        }

        return true;
    }

    /**
     * Execute this command in a separate thread.
     *
     * @param command the command to execute
     */
    private void executeCommand(Command command) {
        LOGGER.debug("Executing {}", command);
        runningCommands.add(command);
        new CommandExecutor(command) {
            @Override
            public void finished() {
                runningCommands.remove(command);
            }
        }.start();
    }

    @Override
    public void run() {
        runner = new Thread() {
            @Override
            public void run() {
                for (; ; ) {
                    if (stopped || !processCommands())
                        break;

                    // wait for 100 ms
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

            }
        };

        runner.setDaemon(true);
        runner.start();
    }

    @Override
    public void stop() {
        stopped = true;
    }
}
