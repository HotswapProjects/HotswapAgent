package org.hotswap.agent.util.classloader;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.scanner.ClassPathScanner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

/**
 * Add agent JAR URL to a classloader. This works only for webapp classloader of URLClassLoader type
 * and webapp first precedence.
 *
 * @author Jiri Bubnik
 */
@Deprecated
public class ClassLoaderURLPatcher implements ClassLoaderPatcher {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassLoaderURLPatcher.class);


    @Override
    public boolean isPatchAvailable(ClassLoader classLoader) {
        return classLoader instanceof URLClassLoader;
    }

    @Override
    public void patch(ClassLoader classLoaderFrom, String pluginPath, ClassLoader classLoader, ProtectionDomain protectionDomain) {
        Set<ClassLoader> patchedClassLoaders = new HashSet<ClassLoader>();
        if (classLoader != getClass().getClassLoader() && !patchedClassLoaders.contains(classLoader)) {
            synchronized (patchedClassLoaders) {
                if (!patchedClassLoaders.contains(classLoader)) {
                    patchedClassLoaders.add(classLoader);
                    if (classLoader instanceof URLClassLoader) {
                        doPatchUrlClassLoader((URLClassLoader) classLoader);
                    } else {
                        LOGGER.debug("Unable to patch ClassLoader {} of type {}. Only URLClassLoader(s) can be currently patched.",
                                classLoader, classLoader.getClass().getName());
                    }
                }
            }
        }
    }

    private static final String CHECK_RESOURCE = "org/hotswap/agent/config/PluginManager.class";

    private void doPatchUrlClassLoader(URLClassLoader classLoader) {

        Class<URLClassLoader> classLoaderClass = URLClassLoader.class;

        try {
            String url = getClass().getClassLoader().getResource(CHECK_RESOURCE).toURI().toString();
            url = url.substring(0, url.length() - CHECK_RESOURCE.length());

            url = removeJarPrefixSuffix(url);

            Field field = classLoaderClass.getDeclaredField("ucp");
            field.setAccessible(true);
            Object ucp = field.get(classLoader);


            Method method = classLoaderClass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(classLoader, new Object[]{new URL(url)});

            LOGGER.debug("Classloader {} patched - Added URL '{}' to classpath.", classLoader, url);

        } catch (Throwable e) {
            LOGGER.error("Error patching classloader {}", e, classLoader);
        }
    }

    /**
     * Resolve JAR file path in format: "jar:file:/J:/HotSwapAgent/target/HotSwapAgent-1.0-SNAPSHOT.jar!/"
     * to a valid URL file:/J:/HotSwapAgent/target/HotSwapAgent-1.0-SNAPSHOT.jar
     *
     * @param url URL to resolve
     * @return modified URL
     */
    private String removeJarPrefixSuffix(String url) {
        if (url.startsWith(ClassPathScanner.JAR_URL_PREFIX)) {
            url = url.substring(ClassPathScanner.JAR_URL_PREFIX.length());
        }

        if (url.endsWith(ClassPathScanner.JAR_URL_SEPARATOR)) {
            url = url.substring(0, url.length() - ClassPathScanner.JAR_URL_SEPARATOR.length());
        }

        return url;
    }

}
