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

import org.hotswap.agent.annotation.handler.WatchEventCommand;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default command scheduler implementation.
 *
 * @author Jiri Bubnik
 */
public class SchedulerImpl implements Scheduler {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SchedulerImpl.class);

    int DEFAULT_SCHEDULING_TIMEOUT = 100;

    // TODO : Some commands must be executed in the order in which they are put to scheduler. Therefore
    //        there could be a LinkedHashMap and CommandExecutor should be singleton for commands that
    //        must be executed in order. There is an issue related to this problem
    //        https://github.com/HotswapProjects/HotswapAgent/issues/39  which requires concurrent using
    final Map<Command, DuplicateScheduleConfig> scheduledCommands = new ConcurrentHashMap<>();
    final Set<Command> runningCommands = Collections.synchronizedSet(new HashSet<Command>());

    Thread runner;
    boolean stopped;

    @Override
    public void scheduleCommand(Command command) {
        scheduleCommand(command, DEFAULT_SCHEDULING_TIMEOUT);
    }

    @Override
    public void scheduleCommand(Command command, int timeout) {
        scheduleCommand(command, timeout, DuplicateSheduleBehaviour.WAIT_AND_RUN_AFTER);
    }

    @Override
    public void scheduleCommand(Command command, int timeout, DuplicateSheduleBehaviour behaviour) {
        synchronized (scheduledCommands) {
            Command targetCommand = command;
            if (scheduledCommands.containsKey(command) && (command instanceof MergeableCommand)) {
                // get existing equals command and merge it
                for (Command scheduledCommand : scheduledCommands.keySet()) {
                    if (command.equals(scheduledCommand)) {
                        targetCommand = ((MergeableCommand) scheduledCommand).merge(command);
                        break;
                    }
                }
            }

            // map may already contain equals command, put will replace it and reset timer
            scheduledCommands.put(targetCommand, new DuplicateScheduleConfig(System.currentTimeMillis() + timeout, behaviour));
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
            for (Iterator<Map.Entry<Command, DuplicateScheduleConfig>> it = scheduledCommands.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Command, DuplicateScheduleConfig> entry = it.next();
                DuplicateScheduleConfig config = entry.getValue();
                Command command = entry.getKey();

                // if timeout
                if (config.getTime() < currentTime) {
                    // command is currently running
                    if (runningCommands.contains(command)) {
                        if (config.getBehaviour().equals(DuplicateSheduleBehaviour.SKIP)) {
                            LOGGER.debug("Skipping duplicate running command {}", command);
                            it.remove();
                        } else if (config.getBehaviour().equals(DuplicateSheduleBehaviour.RUN_DUPLICATE)) {
                            executeCommand(command);
                            it.remove();
                        }
                    } else {
                        executeCommand(command);
                        it.remove();
                    }
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
        if (command instanceof WatchEventCommand)
            LOGGER.trace("Executing {}", command); // too much output for debug
        else
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

    private static class DuplicateScheduleConfig {
        // time when to run
        long time;

        // behaviour in case of conflict (running same command in progress)
        DuplicateSheduleBehaviour behaviour;

        private DuplicateScheduleConfig(long time, DuplicateSheduleBehaviour behaviour) {
            this.time = time;
            this.behaviour = behaviour;
        }

        public long getTime() {
            return time;
        }

        public DuplicateSheduleBehaviour getBehaviour() {
            return behaviour;
        }
    }
}
