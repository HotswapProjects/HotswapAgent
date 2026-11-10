/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.wildfly.el;

import java.lang.reflect.Method;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Purge org.jboss.el.cache.BeanPropertiesCache of the classes of a class loader.
 *
 * @author alpapad@gmail.com
 */
public class PurgeWildFlyBeanELResolverCacheCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeWildFlyBeanELResolverCacheCommand.class);

    /** The class loader which resolves BeanPropertiesCache. */
    private final ClassLoader appClassLoader;

    /** The class loader of the classes to remove from the cache. */
    private final ClassLoader classLoader;

    public PurgeWildFlyBeanELResolverCacheCommand(ClassLoader appClassLoader, ClassLoader classLoader) {
        this.appClassLoader = appClassLoader;
        this.classLoader = classLoader;
    }

    @Override
    public void executeCommand() {
        LOGGER.debug("Cleaning BeanPropertiesCache of {}.", classLoader);
        try {
            Class<?> cacheClass = Class.forName("org.jboss.el.cache.BeanPropertiesCache", true, appClassLoader);
            // SoftConcurrentHashMap keeps the entries in an inner map, getProperties().clear() would not remove them
            Method clear = cacheClass.getDeclaredMethod("clear", ClassLoader.class);
            clear.setAccessible(true);
            clear.invoke(null, classLoader);
        } catch (Exception e) {
            LOGGER.error("Error cleaning BeanPropertiesCache of {}.", e, classLoader);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PurgeWildFlyBeanELResolverCacheCommand that = (PurgeWildFlyBeanELResolverCacheCommand) o;
        return appClassLoader.equals(that.appClassLoader) && classLoader.equals(that.classLoader);
    }

    @Override
    public int hashCode() {
        return 31 * appClassLoader.hashCode() + classLoader.hashCode();
    }

    @Override
    public String toString() {
        return "PurgeWildFlyBeanELResolverCacheCommand{appClassLoader=" + appClassLoader + ", classLoader=" + classLoader + '}';
    }
}
