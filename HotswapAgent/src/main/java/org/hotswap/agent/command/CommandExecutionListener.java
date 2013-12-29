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
