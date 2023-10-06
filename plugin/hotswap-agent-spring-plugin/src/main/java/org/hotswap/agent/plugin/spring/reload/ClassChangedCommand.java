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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Add changed Class to SpringChangedAgent.
 */
public class ClassChangedCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassChangedCommand.class);
    private static final Set<String> IGNORE_PACKAGES = new HashSet<>();

    ClassLoader appClassLoader;

    Class clazz;

    Scheduler scheduler;

    static {
        IGNORE_PACKAGES.add("org.hotswap.agent.plugin.spring.reload");
        IGNORE_PACKAGES.add("org.hotswap.agent.plugin.spring.scanner");
    }

    public ClassChangedCommand(ClassLoader appClassLoader, Class clazz, Scheduler scheduler) {
        this.appClassLoader = appClassLoader;
        this.clazz = clazz;
        this.scheduler = scheduler;
    }

    @Override
    public void executeCommand() {
        try {
            Class<?> targetClass = Class.forName("org.hotswap.agent.plugin.spring.reload.SpringChangedAgent", true, appClassLoader);
            Method targetMethod = targetClass.getDeclaredMethod("addChangedClass", Class.class);
            targetMethod.invoke(null, clazz);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error invoking method", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        }
    }
}
