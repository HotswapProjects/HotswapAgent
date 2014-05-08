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
     * Run the scheduler agent thread.
     */
    void run();

    /**
     * Stop the scheduler agent thread.
     */
    void stop();
}
