package org.hotswap.agent.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Command that can merge multiple commands into a single execution.
 * <p/>
 * ${Scheduler.schedule()} compares existing scheduled commands with equlas() method and
 * if the command exists, it is replaced by new command and timer is restarted - effectively
 * forgetting firs command. This implementation provides a way to get all merged commands
 * and process them in the execution. It is also an interface to override merge method
 * to provide custom behaviour.
 * <p/>
 * For example - merge multiple MODIFY events into one, or if DELETE + CREATE events are scheduled,
 * merge them into single MODIFY event.
 */
public abstract class MergeableCommand implements Command {

    List<Command> mergedCommands = new ArrayList<Command>();

    /**
     * Merge commands
     *
     * @param other the other command to merge to
     * @return merge result, by default returns this instance.
     */
    public Command merge(Command other) {
        mergedCommands.add(other);
        return this;
    }

    public List<Command> getMergedCommands() {
        return mergedCommands;
    }

    /**
     * Return merged commands and clear internal list
     *
     * @return the list of merged commands
     */
    public List<Command> popMergedCommands() {
        List<Command> result = new ArrayList<>(mergedCommands);
        mergedCommands.clear();
        return result;
    }
}
