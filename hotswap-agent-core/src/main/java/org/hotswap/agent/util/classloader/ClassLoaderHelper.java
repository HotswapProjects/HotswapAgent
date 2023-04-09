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
package org.hotswap.agent.util.classloader;

import java.lang.reflect.Method;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Utility method for classloaders.
 */
public class ClassLoaderHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassLoaderHelper.class);

    public static Method findLoadedClass;

    static {
        try {
            findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
            findLoadedClass.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Unexpected: failed to get ClassLoader findLoadedClass method", e);
        }
    }


    /**
     * Check if the class was already loaded by the classloader. It does not try to load the class
     * (opposite to Class.forName()).
     *
     * @param classLoader classLoader to check
     * @param className fully qualified class name
     * @return true if the class was loaded
     */
    public static boolean isClassLoaded(ClassLoader classLoader, String className) {
        try {
            return findLoadedClass.invoke(classLoader, className) != null;
        } catch (Exception e) {
            LOGGER.error("Unable to invoke findLoadedClass on classLoader {}, className {}", e, classLoader, className);
            return false;
        }
    }

    /**
     * Some class loader has activity state. e.g. WebappClassLoader must be started before it can be used
     *
     * @param classLoader the class loader
     * @return true, if is class loader active
     */
    public static boolean isClassLoderStarted(ClassLoader classLoader) {

        String classLoaderClassName = (classLoader != null) ? classLoader.getClass().getName() : null;

        // TODO: use interface instead of this hack
        if ("org.glassfish.web.loader.WebappClassLoader".equals(classLoaderClassName)||
            "org.apache.catalina.loader.WebappClassLoader".equals(classLoaderClassName) ||
            "org.apache.catalina.loader.ParallelWebappClassLoader".equals(classLoaderClassName) ||
            "org.apache.tomee.catalina.TomEEWebappClassLoader".equals(classLoaderClassName) ||
            "org.springframework.boot.web.embedded.tomcat.TomcatEmbeddedWebappClassLoader".equals(classLoaderClassName)
            )
        {
            try {
                Class<?> clazz = classLoader.getClass();
                boolean isStarted;
                if ("org.apache.catalina.loader.WebappClassLoaderBase".equals(clazz.getSuperclass().getName())) {
                    clazz = clazz.getSuperclass();
                    isStarted = "STARTED".equals((String) ReflectionHelper.invoke(classLoader, clazz, "getStateName", new Class[] {}, null));
                } else {
                    isStarted = (boolean) ReflectionHelper.invoke(classLoader, clazz, "isStarted", new Class[] {}, null);
                }
                return isStarted;
            } catch (Exception e) {
                LOGGER.warning("isClassLoderStarted() : {}", e.getMessage());
            }
        }
        return true;
    }
}
