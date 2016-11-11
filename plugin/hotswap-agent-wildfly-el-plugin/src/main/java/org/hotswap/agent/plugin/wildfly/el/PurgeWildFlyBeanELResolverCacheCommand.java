/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                    Method beanElResolverMethod = appClassLoader.loadClass("org.jboss.el.cache.BeanPropertiesCache").getDeclaredMethod("getProperties", new Class<?>[] {});
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
