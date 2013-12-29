package org.hotswap.agent.command;

/**
 * Schedule a command to run.
 *
 * @author Jiri Bubnik
 */
public interface Scheduler {

    /**
     * Schedule new command for execution.
     * <p/>
     * If the command (or another same instance by equals) is already scheduled, then the
     * old instance is replaced by this command and timer is restarted.
     * <p/>
     * The command contains information about target classloader where it should be run.
     *
     * @param command the command to execute
     */
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
