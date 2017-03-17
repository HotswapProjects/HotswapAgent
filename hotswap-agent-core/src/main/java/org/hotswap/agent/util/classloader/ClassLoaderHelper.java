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
     * @return true, if is class loder active
     */
    public static boolean isClassLoderStarted(ClassLoader classLoader) {
        if ("org.glassfish.web.loader.WebappClassLoader".equals(classLoader.getClass().getName())||
            "org.apache.catalina.loader.WebappClassLoader".equals(classLoader.getClass().getName())) {
            try {
                Class<?> clazz = classLoader.getClass();
                if ("org.apache.catalina.loader.WebappClassLoaderBase".equals(clazz.getSuperclass().getName())) {
                    clazz = clazz.getSuperclass();
                }
                boolean isStarted = (boolean) ReflectionHelper.invoke(classLoader, clazz, "isStarted", new Class[] {}, null);
                return isStarted;
            } catch (Exception e) {
                LOGGER.warning("isClassLoderStarted() : {}", e.getMessage());
            }
        }
        return true;
    }
}
