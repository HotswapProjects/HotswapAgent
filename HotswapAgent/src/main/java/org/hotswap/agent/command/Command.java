package org.hotswap.agent.command;

/**
 * Command to execute via scheduler.
 *
 * @author Jiri Bubnik
 * @see org.hotswap.agent.command.Scheduler
 * @see org.hotswap.agent.command.impl.CommandExecutor
 */
public interface Command {

    /**
     * Execute the command.
     */
    public void executeCommand();
}
