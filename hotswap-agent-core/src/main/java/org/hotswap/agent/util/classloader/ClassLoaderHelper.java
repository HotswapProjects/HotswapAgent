package org.hotswap.agent.util.classloader;

import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
}
