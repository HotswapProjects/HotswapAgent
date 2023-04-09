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
package org.hotswap.agent.plugin.wildfly.el;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Purge BeanPropertiesCache && FactoryFinderCache.
 *
 * @author alpapad@gmail.com
 */
public class PurgeWildFlyBeanELResolverCacheCommand extends MergeableCommand {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeWildFlyBeanELResolverCacheCommand.class);

    /** The app class loader. */
    private ClassLoader appClassLoader;

    /** The class name. */
    private String className;

    /**
     * Instantiates a new purge wild fly bean el resolver cache command.
     *
     * @param appClassLoader the app class loader
     * @param className the class name
     */
    public PurgeWildFlyBeanELResolverCacheCommand(ClassLoader appClassLoader, String className) {
        this.appClassLoader = appClassLoader;
        this.className = className;
    }

    /* (non-Javadoc)
     * @see org.hotswap.agent.command.Command#executeCommand()
     */
    @Override
    public void executeCommand() {
        LOGGER.info("Cleaning  BeanPropertiesCache {} {}.", className, appClassLoader);
        if (className != null) {
            try {
                ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

                try {
                    Thread.currentThread().setContextClassLoader(appClassLoader);
                    Class<?> cacheClazz = Class.forName("org.jboss.el.cache.BeanPropertiesCache", true, appClassLoader);
                    Method beanElResolverMethod = cacheClazz.getDeclaredMethod("getProperties", new Class<?>[] {});
                    Object o = beanElResolverMethod.invoke(null);

                    @SuppressWarnings("unchecked")
                    Map<Class<?>, Object> m = Map.class.cast(o);

                    Iterator<Map.Entry<Class<?>, Object>> it = m.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Class<?>, Object> entry = it.next();
                        if(entry.getKey().getClassLoader() == appClassLoader) {
                            if (entry.getKey().getName().equals(className) || (entry.getKey().getName()).equals(className + "$Proxy$_$$_WeldSubclass")) {
                                it.remove();
                            }
                        }
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(oldContextClassLoader);
                }
            } catch (Exception e) {
                LOGGER.error("Error cleaning BeanPropertiesCache. {}", e, className);
            }
        } else {

            try {
                LOGGER.info("Cleaning  BeanPropertiesCache {}.", appClassLoader);
                Method beanElResolverMethod = resolveClass("org.jboss.el.cache.BeanPropertiesCache").getDeclaredMethod("clear", ClassLoader.class);
                beanElResolverMethod.setAccessible(true);
                beanElResolverMethod.invoke(null, appClassLoader);
            } catch (Exception e) {
                LOGGER.error("Error cleaning BeanPropertiesCache. {}", e, appClassLoader);
            }
            try {
                LOGGER.info("Cleaning  FactoryFinderCache {}.", appClassLoader);
                Method beanElResolverMethod = resolveClass("org.jboss.el.cache.FactoryFinderCache").getDeclaredMethod("clearClassLoader", ClassLoader.class);
                beanElResolverMethod.setAccessible(true);
                beanElResolverMethod.invoke(null, appClassLoader);
            } catch (Exception e) {
                LOGGER.error("Error cleaning FactoryFinderCache. {}", e, appClassLoader);
            }
        }
    }

    /**
     * Resolve class.
     *
     * @param name the name
     * @return the class
     * @throws ClassNotFoundException the class not found exception
     */
    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PurgeWildFlyBeanELResolverCacheCommand that = (PurgeWildFlyBeanELResolverCacheCommand) o;

        if (!appClassLoader.equals(that.appClassLoader))
            return false;

        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "PurgeWildFlyBeanELResolverCacheCommand{" + "appClassLoader=" + appClassLoader + '}';
    }
}
