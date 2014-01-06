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
