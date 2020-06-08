package org.hotswap.agent.config;

import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;

public class ScheduledHotswapCommand extends MergeableCommand {
    private Map<Class<?>, byte[]> reloadMap;

    public ScheduledHotswapCommand(Map<Class<?>, byte[]> reloadMap) {
        this.reloadMap = new HashMap<>();
        for (Class<?> key: reloadMap.keySet()) {
            this.reloadMap.put(key, reloadMap.get(key));
        }
    }

    public Command merge(Command other) {
        if (other instanceof ScheduledHotswapCommand) {
            ScheduledHotswapCommand scheduledHotswapCommand = (ScheduledHotswapCommand) other;
            for (Class<?> key: scheduledHotswapCommand.reloadMap.keySet()) {
                this.reloadMap.put(key, scheduledHotswapCommand.reloadMap.get(key));
            }
        }
        return this;
    }

    @Override
    public void executeCommand() {
        PluginManager.getInstance().hotswap(reloadMap);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o || getClass() == o.getClass()) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }

}
