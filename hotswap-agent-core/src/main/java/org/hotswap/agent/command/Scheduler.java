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
package org.hotswap.agent.command;

/**
 * Schedule a command to run.
 *
 * @author Jiri Bubnik
 */
public interface Scheduler {

    enum DuplicateSheduleBehaviour {
        SKIP,
        WAIT_AND_RUN_AFTER,
        RUN_DUPLICATE
    }

    /**
     * Schedule a new command for execution.
     * <p/>
     * If the command (or another same instance by equals) is already scheduled, then the
     * old instance is replaced by this command and the timer is restarted.
     * <p/>
     * The command contains information about the target classloader where it should be run.
     * <p/>
     * The default timeout 100ms is assumed. If another (equals) command is scheduled before
     * the timeout expires, it replaced (or merged). Otherwise the command is executed at timeout.
     *
     * @param command the command to execute
     */
    void scheduleCommand(Command command);

    /**
     * Schedule a new command for execution.
     * <p/>
     *
     * @param command the command to execute
     * @param timeout timeout after which the command is executed
     */
    void scheduleCommand(Command command, int timeout);

    /**
     * Schedule a new command for execution.
     * <p/>
     *
     * @param command the command to execute
     * @param timeout timeout after which the command is executed
     * @param behaviour if another instance of this commands runs on schedule or within timeout, should we skip it?
     */
    void scheduleCommand(Command command, int timeout, DuplicateSheduleBehaviour behaviour);

    /**
     * Schedule command on classes redefined or timeout.
     *
     * @param command the command
     * @param timeout the timeout
     */
    void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout);

    /**
     * Schedule command on classes redefined or timeout.
     *
     * @param command   the command
     * @param timeout   the timeout
     * @param behaviour the behaviour
     */
    void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout, DuplicateSheduleBehaviour behaviour);

    /**
     * Run the scheduler agent thread.
     */
    void run();

    /**
     * Stop the scheduler agent thread.
     */
    void stop();

    /**
     * This method is invoked when classes are redefined.
     *
     * It is expected to be used as a callback for performing necessary actions
     * or processing related to the redefinition of classes, such as updating
     * persisted states, managing classloader-specific resources, or triggering
     * dependent workflows.
     */
    void onClassesRedefined();

}
