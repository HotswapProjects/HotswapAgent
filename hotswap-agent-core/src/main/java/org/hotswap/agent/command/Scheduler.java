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
package org.hotswap.agent.command;

import java.util.concurrent.TimeUnit;

/**
 * Schedule a command to run.
 *
 * @author Jiri Bubnik
 */
public interface Scheduler {

    public static enum DuplicateSheduleBehaviour {
        SKIP,
        WAIT_AND_RUN_AFTER,
        RUN_DUPLICATE
    }

    /**
     * Schedule new command for execution.
     * <p/>
     * If the command (or another same instance by equals) is already scheduled, then the
     * old instance is replaced by this command and timer is restarted.
     * <p/>
     * The command contains information about target classloader where it should be run.
     * <p/>
     * The default timeout 100ms is assumed. If another (equals) command is scheduled before
     * the timeout expires, it replaced (or merged). Otherwise the command is executed at timeout.
     *
     * @param command the command to execute
     */
    void scheduleCommand(Command command);

    /**
     * Schedule new command for execution.
     * <p/>
     *
     * @param command the command to execute
     * @param timeout timeout after which the command is executed
     */
    void scheduleCommand(Command command, int timeout);

    /**
     * Schedule new command for execution.
     * <p/>
     *
     * @param command the command to execute
     * @param timeout timeout after which the command is executed
     * @param behaviour if another instance of this commands runs on schedule or within timeout, should we skip it?
     */
    void scheduleCommand(Command command, int timeout, DuplicateSheduleBehaviour behaviour);

    /**
     * Run the scheduler agent thread.
     */
    void run();

    /**
     * Stop the scheduler agent thread.
     */
    void stop();
}
