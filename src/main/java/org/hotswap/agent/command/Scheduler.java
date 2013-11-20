package org.hotswap.agent.command;

/**
 * Schedule a command to run.
 *
 * @author Jiri Bubnik
 */
public interface Scheduler {
    void scheduleCommand(Command command);

    /**
     * Run the scheduler agent thread.
     */
    void run();

    /**
     * Stop the scheduler agent thread.
     */
    void stop();
}
