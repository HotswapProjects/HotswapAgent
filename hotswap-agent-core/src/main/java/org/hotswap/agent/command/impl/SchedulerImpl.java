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

import org.hotswap.agent.annotation.handler.WatchEventCommand;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    final Map<Command, ScheduleCommandConfig> scheduledCommands = new ConcurrentHashMap<>();
    final Set<Command> runningCommands = Collections.synchronizedSet(new HashSet<>());

    final boolean isOnClsRedefinedSupported;

    // a redefinition, the end of which the JVM does not report, is supposed to be finished after this time
    private static final int REDEFINITION_TIMEOUT = 10000;

    // 0 for no limit
    private volatile int maxCommandThreads;
    private final AtomicInteger commandThreads = new AtomicInteger();
    private boolean threadStartFailed;
    // the scheduler waits on it for the next cycle
    private final Object cycle = new Object();

    private final AtomicInteger redefinitionSteps = new AtomicInteger();
    private volatile long redefinitionStepFinished;
    // JBR reports the end of a redefinition only with the enhanced class redefinition
    private volatile boolean redefinitionReported;
    private volatile long redefinitionEndExpected;
    Thread runner;
    volatile boolean stopped;

    public SchedulerImpl(boolean isOnClsRedefinedSupported) {
        this.isOnClsRedefinedSupported = isOnClsRedefinedSupported;
    }

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
        doScheduleCommand(command, timeout, behaviour, false);
    }

    @Override
    public void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout) {
        scheduleCommandOnClassesRedefinedOrTimeout(command, timeout, DuplicateSheduleBehaviour.WAIT_AND_RUN_AFTER);
    }

    @Override
    public void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout, DuplicateSheduleBehaviour behaviour) {
        doScheduleCommand(command, timeout, behaviour, true);
    }

    public void doScheduleCommand(Command command, int timeout, DuplicateSheduleBehaviour behaviour, boolean isOnClassRedefined) {
        synchronized (scheduledCommands) {
            Command targetCommand = command;
            if ((command instanceof MergeableCommand) && scheduledCommands.containsKey(command)) {
                // get existing equals command and merge it
                for (Command scheduledCommand : scheduledCommands.keySet()) {
                    if (command.equals(scheduledCommand)) {
                        targetCommand = ((MergeableCommand) scheduledCommand).merge(command);
                        break;
                    }
                }
            }

            // map may already contain equals command, put will replace it and reset timer
            scheduledCommands.put(targetCommand, new ScheduleCommandConfig(timeout, behaviour, isOnClassRedefined));
            LOGGER.trace("{} scheduled for execution in {}ms", targetCommand, timeout);
        }
    }

    /**
     * One cycle of the scheduler agent. Process all commands which are not currently
     * running and time lower than current milliseconds.
     *
     * @return true if the agent should continue (false for fatal error)
     */
    private boolean processCommands(boolean isOnClassRedefined) {
        Long currentTime = System.currentTimeMillis();
        boolean useOnClassRedefined = isOnClsRedefinedSupported && isOnClassRedefined;
        synchronized (scheduledCommands) {
            for (Map.Entry<Command, ScheduleCommandConfig> entry : commandsToProcess()) {
                ScheduleCommandConfig config = entry.getValue();
                Command command = entry.getKey();

                // if timeout
                if (useOnClassRedefined && config.isOnClassRedefined
                        || config.getTime() < currentTime && !isRedefining(config, currentTime)) {
                    // command is currently running
                    if (runningCommands.contains(command)) {
                        if (config.getBehaviour().equals(DuplicateSheduleBehaviour.SKIP)) {
                            LOGGER.debug("Skipping duplicate running command {}", command);
                            scheduledCommands.remove(command);
                        } else if (config.getBehaviour().equals(DuplicateSheduleBehaviour.RUN_DUPLICATE)) {
                            if (!executeCommand(command)) {
                                break;
                            }
                            scheduledCommands.remove(command);
                        }
                    } else {
                        if (!executeCommand(command)) {
                            break;
                        }
                        scheduledCommands.remove(command);
                    }
                }
            }
        }

        return true;
    }

    // with a limit of threads commands have to wait, the ones scheduled for an earlier time go first then
    private Collection<Map.Entry<Command, ScheduleCommandConfig>> commandsToProcess() {
        if (maxCommandThreads <= 0) {
            return scheduledCommands.entrySet();
        }
        List<Map.Entry<Command, ScheduleCommandConfig>> commands = new ArrayList<>(scheduledCommands.entrySet());
        commands.sort(Comparator.comparingLong(entry -> entry.getValue().getTime()));
        return commands;
    }

    private boolean isRedefining(ScheduleCommandConfig config, long currentTime) {
        return config.isOnClassRedefined && (redefinitionSteps.get() > 0 || currentTime < redefinitionEndExpected
                || currentTime <= redefinitionStepFinished + config.timeout);
    }

    /**
     * Execute this command in a separate thread.
     *
     * @param command the command to execute
     * @return false if the command has to wait for the next cycle: the limit of threads is reached or the thread
     * was not started
     */
    private boolean executeCommand(Command command) {
        int limit = maxCommandThreads;
        if (limit > 0 && commandThreads.get() >= limit) {
            return false;
        }
        commandThreads.incrementAndGet();
        boolean added = false;
        try {
            added = runningCommands.add(command);
            start(new CommandExecutor(command) {
                @Override
                public void finished() {
                    commandThreads.decrementAndGet();
                    runningCommands.remove(command);
                    if (maxCommandThreads > 0) {
                        // the thread is free for the next command
                        synchronized (cycle) {
                            cycle.notify();
                        }
                    }
                }
            });
        } catch (Throwable t) {
            // e.g. OutOfMemoryError: unable to create native thread, it must not stop the scheduler
            commandThreads.decrementAndGet();
            if (added) {
                runningCommands.remove(command);
            }
            if (threadStartFailed) {
                LOGGER.debug("Unable to start the thread of {}, the command stays scheduled", t, command);
            } else {
                LOGGER.error("Unable to start the thread of {}, the command stays scheduled", t, command);
            }
            threadStartFailed = true;
            return false;
        }
        threadStartFailed = false;
        if (command instanceof WatchEventCommand)
            LOGGER.trace("Executing {}", command); // too much output for debug
        else
            LOGGER.debug("Executing {}", command);
        return true;
    }

    void start(CommandExecutor executor) {
        executor.start();
    }

    @Override
    public void setMaxCommandThreads(int maxCommandThreads) {
        this.maxCommandThreads = maxCommandThreads;
    }

    @Override
    public void run() {
        runner = new Thread() {
            @Override
            public void run() {
            for (; ; ) {
                try {
                    if (stopped || !processCommands(false))
                        break;
                } catch (Throwable t) {
                    LOGGER.error("Error processing the scheduled commands, the scheduler continues", t);
                }

                // wait for 100 ms, or for a thread of a finished command
                try {
                    synchronized (cycle) {
                        cycle.wait(100);
                    }
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

    @Override
    public void onRedefinitionStepStarted() {
        redefinitionSteps.incrementAndGet();
        if (redefinitionReported) {
            redefinitionEndExpected = System.currentTimeMillis() + REDEFINITION_TIMEOUT;
        }
    }

    @Override
    public void onRedefinitionStepFinished() {
        redefinitionStepFinished = System.currentTimeMillis();
        redefinitionSteps.decrementAndGet();
    }

    @Override
    public void onClassesRedefined() {
        redefinitionReported = true;
        redefinitionEndExpected = 0;
        processCommands(true);
    }

    private static class ScheduleCommandConfig {
        int timeout;

        // time when to run
        long time;

        // behaviour in case of conflict (running same command in progress)
        DuplicateSheduleBehaviour behaviour;

        // could be called after class redefinition
        boolean isOnClassRedefined;

        private ScheduleCommandConfig(int timeout, DuplicateSheduleBehaviour behaviour, boolean isOnClassRedefined)
        {
            this.timeout = timeout;
            this.time = System.currentTimeMillis() + timeout;
            this.behaviour = behaviour;
            this.isOnClassRedefined = isOnClassRedefined;
        }

        public long getTime() {
            return time;
        }

        public DuplicateSheduleBehaviour getBehaviour() {
            return behaviour;
        }

        public boolean isOnClassRedefined() {
            return isOnClassRedefined;
        }
    }
}
