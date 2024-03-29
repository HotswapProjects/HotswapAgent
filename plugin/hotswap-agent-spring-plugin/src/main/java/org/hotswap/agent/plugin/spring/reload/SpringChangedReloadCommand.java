/*
 * Copyright 2013-2024 the HotswapAgent authors.
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

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The type Spring changed reload command.
 */
public class SpringChangedReloadCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlsChangedCommand.class);

    // unit test only
    private static AtomicLong waitingTaskCount = new AtomicLong(0);

    ClassLoader appClassLoader;
    long timestamps;

    public SpringChangedReloadCommand(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
        this.timestamps = System.currentTimeMillis();
        LOGGER.trace("SpringChangedReloadCommand created with timestamp '{}'", timestamps);
        waitingTaskCount.incrementAndGet();
    }

    @Override
    public void executeCommand() {
        // async call to avoid reload too much times
        try {
            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.reload.SpringChangedAgent", true, appClassLoader);
            Method method = clazz.getDeclaredMethod(
                    "reload", long.class);
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
            waitingTaskCount.decrementAndGet();
        }
    }

    // this is used by tests
    public static boolean isEmptyTask() {
        return waitingTaskCount.get() == 0;
    }
}
