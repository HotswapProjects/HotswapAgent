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

    List<Command> mergedCommands = new ArrayList<>();

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
