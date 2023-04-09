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

/**
 * Listen to command execution.
 * <p/>
 * Use this listener to get command result.
 *
 * @author Jiri Bubnik
 */
public interface CommandExecutionListener {

    /**
     * Command was executed and the method returned result (or null for void methods).
     *
     * @param result result of target method invocation.
     */
    void commandExecuted(Object result);
}
