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
package org.hotswap.agent.plugin.elresolver;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Flushe JBoss ReflectionUtil caches
 */
public class PurgeJbossReflectionUtil  extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeBeanELResolverCacheCommand.class);

    private ClassLoader appClassLoader;

    public PurgeJbossReflectionUtil(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
    }

    @Override
    public void executeCommand() {
        try {
            LOGGER.debug("Flushing Jboss ReflectionUtil");
            Class<?> reflectionUtilClass = appClassLoader.loadClass("org.jboss.el.util.ReflectionUtil");
            Object cache = ReflectionHelper.get(null, reflectionUtilClass, "methodCache");
            ReflectionHelper.invoke(cache, cache.getClass(), "clear", null);
        } catch (Exception e) {
            LOGGER.error("executeCommand() exception {}.", e.getMessage());
        }
    }

}
