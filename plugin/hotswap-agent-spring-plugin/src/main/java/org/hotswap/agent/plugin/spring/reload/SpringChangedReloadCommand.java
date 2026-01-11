/*
 * Copyright 2013-2025 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The type Spring changed reload command.
 */
public class SpringChangedReloadCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlsChangedCommand.class);

    // unit test only
    private static AtomicLong waitingTaskCount = new AtomicLong(0);
    private static boolean isInitialTaskCountHysteresis = false;

    ClassLoader appClassLoader;
    long timestamps;
    Command subCommand;

    public SpringChangedReloadCommand(ClassLoader appClassLoader, Command subCommand) {
        this.appClassLoader = appClassLoader;
        this.timestamps = System.currentTimeMillis();
        this.subCommand = subCommand;
        LOGGER.trace("SpringChangedReloadCommand created with timestamp '{}'", timestamps);
        waitingTaskCount.incrementAndGet();
    }

    public Command merge(Command other) {
        Command result = super.merge(other);
        waitingTaskCount.decrementAndGet(); // other will not be executed
        return result;
    }

    @Override
    public void executeCommand() {
        // async call to avoid reload too much times
        try {
            executeSubCommands();
            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.reload.SpringChangedAgent", true, appClassLoader);
            Method method = clazz.getDeclaredMethod("reload", long.class);
            method.invoke(null, timestamps);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error invoking method", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        } finally {
            long taskCount = waitingTaskCount.decrementAndGet();
            if (isInitialTaskCountHysteresis && taskCount == 1) {
                isInitialTaskCountHysteresis = false;
                waitingTaskCount.decrementAndGet();
            }
        }
    }

    private void executeSubCommands() {
        List<Command> mergedCommands = new ArrayList<>();
        mergedCommands.add(0, this);
        mergedCommands.addAll(popMergedCommands());
        do {
            for (Command command : mergedCommands) {
                try {
                    SpringChangedReloadCommand changedReloadCommand = (SpringChangedReloadCommand) command;
                    if (changedReloadCommand.subCommand != null) {
                        changedReloadCommand.subCommand.executeCommand();
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Error executing command", e);
                }
            }
            mergedCommands = popMergedCommands();
        } while (!mergedCommands.isEmpty());
    }

    // this is used by tests
    public static boolean isEmptyTask() {
        return waitingTaskCount.get() == 0;
    }

    public static void setInitialTaskCountHysteresis() {
        if (waitingTaskCount.get() == 0) {
            waitingTaskCount.set(1);
            isInitialTaskCountHysteresis = true;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpringChangedReloadCommand that = (SpringChangedReloadCommand) o;
        if (!appClassLoader.equals(that.appClassLoader)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        return result;
    }
}
