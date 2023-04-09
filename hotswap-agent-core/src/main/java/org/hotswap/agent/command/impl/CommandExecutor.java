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

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Execute a command in a separate thread.
 *
 * @author Jiri Bubnik
 */
public class CommandExecutor extends Thread {
    private static AgentLogger LOGGER = AgentLogger.getLogger(CommandExecutor.class);

    final Command command;

    public CommandExecutor(Command command) {
        this.command = command;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            LOGGER.trace("Executing command {}", command);
            command.executeCommand();
        } finally {
            finished();
        }
    }

    /**
     * Method template to register finish event
     */
    public void finished() {
    }

}
