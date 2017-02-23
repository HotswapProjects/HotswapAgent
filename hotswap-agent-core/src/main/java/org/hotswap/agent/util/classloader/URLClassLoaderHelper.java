package org.hotswap.agent.util.classloader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;

import org.hotswap.agent.logging.AgentLogger;

import sun.misc.URLClassPath;

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
     * @param classLoader    URL classloader
     * @param extraClassPath path to prepend
     */
    public static void prependClassPath(URLClassLoader classLoader, URL[] extraClassPath) {

        synchronized (classLoader) {

            // set new URLClassPath to the classloader via reflection
            try {
                Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
                ucpField.setAccessible(true);


                URL[] origClassPath = getOrigClassPath(classLoader, ucpField);

                URL[] modifiedClassPath = new URL[origClassPath.length + extraClassPath.length];
                System.arraycopy(extraClassPath, 0, modifiedClassPath, 0, extraClassPath.length);
                System.arraycopy(origClassPath, 0, modifiedClassPath, extraClassPath.length, origClassPath.length);


                ucpField.set(classLoader, new ExtraURLClassPath(modifiedClassPath));

                LOGGER.debug("Added extraClassPath URLs {} to classLoader {}", Arrays.toString(extraClassPath), classLoader);
            } catch (Exception e) {
                LOGGER.error("Unable to add extraClassPath URLs {} to classLoader {}", e, Arrays.toString(extraClassPath), classLoader);
            }
        }
    }

    public static void setWatchResourceLoader(URLClassLoader classLoader, final ClassLoader watchResourceLoader) {

        synchronized (classLoader) {

            // set new URLClassPath to the classloader via reflection
            try {
                Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
                ucpField.setAccessible(true);


                URL[] origClassPath = getOrigClassPath(classLoader, ucpField);

                ucpField.set(classLoader, new ExtraURLClassPath(origClassPath, watchResourceLoader));

                LOGGER.debug("WatchResourceLoader registered to classLoader {}", classLoader);
            } catch (Exception e) {
                LOGGER.debug("Unable to register WatchResourceLoader to classLoader {}", e, classLoader);
            }
        }
    }

    private static URL[] getOrigClassPath(URLClassLoader classLoader, Field ucpField) throws IllegalAccessException {
        URL[] origClassPath;
        URLClassPath urlClassPath = (URLClassPath) ucpField.get(classLoader);
        if (urlClassPath instanceof ExtraURLClassPath) {
            origClassPath = ((ExtraURLClassPath)urlClassPath).getOrigClassPath();
        } else {
            origClassPath = classLoader.getURLs();
        }
        return origClassPath;
    }

    private static class ExtraURLClassPath extends sun.misc.URLClassPath {

        private ClassLoader watchResourceLoader;
        URL[] origClassPath;

        public ExtraURLClassPath(URL[] origClassPath) {
            super(origClassPath);
            this.origClassPath = origClassPath;
        }

        public ExtraURLClassPath(URL[] origClassPath, ClassLoader watchResourceLoader) {
            super(origClassPath);
            this.origClassPath = origClassPath;
            this.watchResourceLoader = watchResourceLoader;
        }

        @Override
        public URL findResource(String name, boolean check) {
            if (watchResourceLoader != null) {
                URL resource = watchResourceLoader.getResource(name);
                if (resource != null) {
                    return resource;
                }
            }

            return super.findResource(name, check);
        }

        public Enumeration<URL> findResources(final String name, boolean check) {
            if (watchResourceLoader != null) {
                try {
                    Enumeration<URL> resources = watchResourceLoader.getResources(name);
                    if (resources != null && resources.hasMoreElements()) {
                        return resources;
                    }
                } catch (IOException e) {
                    LOGGER.debug("Unable to load resource {}", e, name);
                }
            }

            return super.findResources(name, check);
        }

        /**
         * Return orig classpath as was set by hotswap agent.
         * Note: cannot use classLoader.getURLs(), because Tomcat WebappClassLoader does not return modified classPath.
         */
        public URL[] getOrigClassPath() {
            return origClassPath;
        }
    }
}
