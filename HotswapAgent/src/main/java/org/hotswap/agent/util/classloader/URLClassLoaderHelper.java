package org.hotswap.agent.util.classloader;

import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Helper methods to enhance URL ClassLoader.
 */
public class URLClassLoaderHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(URLClassLoaderHelper.class);

    /**
     * Insert classpath at the beginning of the classloader path.
     * This implementation will replace ucp field (URLClassPath) with new definition. Any existing Loader
     * is discarded and recreated.
     *
     * @param classLoader URL classloader
     * @param extraClassPath path to prepend
     */
    public static void prependClassPath(URLClassLoader classLoader, URL[] extraClassPath) {

        URL[] origClassPath = classLoader.getURLs();
        URL[] modifiedClassPath = new URL[origClassPath.length + extraClassPath.length];
        System.arraycopy(extraClassPath, 0, modifiedClassPath, 0, extraClassPath.length);
        System.arraycopy(origClassPath, 0, modifiedClassPath, extraClassPath.length, origClassPath.length);

        // set new URLClassPath to the classloader via reflection
        try {
            Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
            ucpField.setAccessible(true);
            ucpField.set(classLoader, new sun.misc.URLClassPath(modifiedClassPath));

            LOGGER.debug("Added extraClassPath URLs {} to classLoader {}", Arrays.toString(extraClassPath), classLoader);
        } catch (Exception e) {
            LOGGER.error("Unable to add extraClassPath URLs {} to classLoader {}", e, Arrays.toString(extraClassPath), classLoader);
        }
    }
}
