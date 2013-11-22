package org.hotswap.agent.util.classloader;

import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Created by bubnik on 22.11.13.
 */
public class URLClassLoaderHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(URLClassLoaderHelper.class);

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
        } catch (Exception e) {
            LOGGER.error("Unable to add extraClassPath URLs {} to classLoader {}", e, extraClassPath, classLoader);
        }


    }
}
