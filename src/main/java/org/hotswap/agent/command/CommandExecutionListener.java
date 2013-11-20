package org.hotswap.agent.command;

/**
 * Listen to command execution.
 * <p/>
 * Use this to get command result.
 *
 * @author Jiri Bubnik
 */
public interface CommandExecutionListener {
    void commandExecuted(Object result);
}
