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

import org.hotswap.agent.javassist.util.proxy.MethodFilter;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.javassist.util.proxy.ProxyObject;
import org.hotswap.agent.logging.AgentLogger;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Helper methods to enhance URL ClassLoader.
 */
public class URLClassPathHelper {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(URLClassPathHelper.class);

    private static Class<?> urlClassPathProxyClass = null;

    static {
        Class<?> urlClassPathClass = null;
        ClassLoader classLoader = URLClassPathHelper.class.getClassLoader();
        try {
            urlClassPathClass = classLoader.loadClass("sun.misc.URLClassPath");
        } catch (ClassNotFoundException e) {
            try {
                // java9
                urlClassPathClass = classLoader.loadClass("jdk.internal.loader.URLClassPath");
            } catch (ClassNotFoundException e1) {
                LOGGER.error("Unable to loadClass URLClassPath!");
            }
        }
        if (urlClassPathClass != null) {
            ProxyFactory f = new ProxyFactory();
            f.setSuperclass(urlClassPathClass);
            f.setFilter(new MethodFilter() {
                public boolean isHandled(Method m) {
                    return !m.getName().equals("finalize");
                }
            });
            urlClassPathProxyClass = f.createClass();
        }
    }

    /**
     * Insert classpath at the beginning of the classloader path.
     * This implementation will replace ucp field (URLClassPath) with new definition. Any existing Loader
     * is discarded and recreated.
     *
     * @param classLoader    classloader
     * @param extraClassPath path to prepend
     */
    public static void prependClassPath(ClassLoader classLoader, URL[] extraClassPath) {
        synchronized (classLoader) {
            // set new URLClassPath to the classloader via reflection
            try {
                Field ucpField = getUcpField(classLoader);
                if (ucpField == null) {
                    LOGGER.debug("Unable to find ucp field in classLoader {}", classLoader);
                    return;
                }

                ucpField.setAccessible(true);

                URL[] origClassPath = getOrigClassPath(classLoader, ucpField);

                URL[] modifiedClassPath = new URL[origClassPath.length + extraClassPath.length];
                System.arraycopy(extraClassPath, 0, modifiedClassPath, 0, extraClassPath.length);
                System.arraycopy(origClassPath, 0, modifiedClassPath, extraClassPath.length, origClassPath.length);

                Object urlClassPath = createClassPathInstance(modifiedClassPath);

                ExtraURLClassPathMethodHandler methodHandler = new ExtraURLClassPathMethodHandler(modifiedClassPath);
                ((Proxy) urlClassPath).setHandler(methodHandler);

                setUcpFieldOfAllClassLoader(classLoader, ucpField, urlClassPath);

                LOGGER.debug("Added extraClassPath URLs {} to classLoader {}", Arrays.toString(extraClassPath), classLoader);
            } catch (Exception e) {
                LOGGER.error("Unable to add extraClassPath URLs {} to classLoader {}", e, Arrays.toString(extraClassPath), classLoader);
            }
        }
    }

    public static void setWatchResourceLoader(ClassLoader classLoader, final ClassLoader watchResourceLoader) {
        synchronized (classLoader) {
            // set new URLClassPath to the classloader via reflection
            try {
                Field ucpField = getUcpField(classLoader);
                if (ucpField == null) {
                    LOGGER.debug("Unable to find ucp field in classLoader {}", classLoader);
                    return;
                }

                ucpField.setAccessible(true);

                URL[] origClassPath = getOrigClassPath(classLoader, ucpField);
                Object urlClassPath = createClassPathInstance(origClassPath);

                ExtraURLClassPathMethodHandler methodHandler = new ExtraURLClassPathMethodHandler(origClassPath, watchResourceLoader);
                ((Proxy) urlClassPath).setHandler(methodHandler);

                setUcpFieldOfAllClassLoader(classLoader, ucpField, urlClassPath);

                LOGGER.debug("WatchResourceLoader registered to classLoader {}", classLoader);
            } catch (Exception e) {
                LOGGER.debug("Unable to register WatchResourceLoader to classLoader {}", e, classLoader);
            }
        }
    }

    private static Object createClassPathInstance(URL[] urls) throws Exception {
        try {
            // java8
            Constructor<?> constr = urlClassPathProxyClass.getConstructor(new Class[]{URL[].class});
            return constr.newInstance(new Object[]{urls});
        } catch (NoSuchMethodException e) {
            // java9
            Constructor<?> constr = urlClassPathProxyClass.getConstructor(new Class[]{URL[].class, AccessControlContext.class});
            return constr.newInstance(new Object[]{urls, null});
        }
    }

    @SuppressWarnings("unchecked")
    private static URL[] getOrigClassPath(ClassLoader classLoader, Field ucpField) throws IllegalAccessException,
            NoSuchFieldException {
        URL[] origClassPath = null;
        Object urlClassPath = ucpField.get(classLoader);

        if (urlClassPath instanceof ProxyObject) {
            ProxyObject p = (ProxyObject) urlClassPath;
            MethodHandler handler = p.getHandler();

            if (handler instanceof ExtraURLClassPathMethodHandler) {
                origClassPath = ((ExtraURLClassPathMethodHandler) handler).getOrigClassPath();
            }
        } else {
            if (classLoader instanceof URLClassLoader) {
                origClassPath = ((URLClassLoader) classLoader).getURLs();
            } else {
                Field pathField = ucpField.getType().getDeclaredField("path");
                pathField.setAccessible(true);
                List<URL> urls = (List<URL>) pathField.get(urlClassPath);
                origClassPath = urls.toArray(new URL[0]);
            }
        }
        return origClassPath;
    }

    /**
     * This method works for the following classloaders:
     * <li>
     *     <ul>JDK 8: sun.misc.Launcher$AppClassLoaders</ul>
     *     <ul>JDK 9 and above: jdk.internal.loader.ClassLoaders$AppClassLoader</ul>
     * </li>
     * In order to make it work on JDK 9 and above, the following JVM argument must be used:
     * <pre>
     *     --add-opens java.base/jdk.internal.loader=ALL-UNNAMED
     * </pre>
     */
    private static Field getUcpField(ClassLoader classLoader) throws NoSuchFieldException {
        if (classLoader instanceof URLClassLoader) {
            return URLClassLoader.class.getDeclaredField("ucp");
        }

        Class<?> ucpOwner = classLoader.getClass();
        if (ucpOwner.getName().startsWith("jdk.internal.loader.ClassLoaders$")) {
            return ucpOwner.getDeclaredField("ucp");
        }

        return null;
    }

    /**
     * jdk.internal.loader.ClassLoaders.AppClassLoader and its super class 'jdk.internal.loader.BuiltinClassLoader' have a field named "ucp".
     * This field is final and private, if it needs to be modified, it needs to be modified in all super classes and the current class.
     *
     * @param classLoader
     * @param ucpField
     * @param urlClassPath
     * @throws IllegalAccessException
     */
    private static void setUcpFieldOfAllClassLoader(ClassLoader classLoader, Field ucpField, Object urlClassPath) throws IllegalAccessException {
        // 1. set the field of current class
        ucpField.set(classLoader, urlClassPath);
        // 2. set the field of all super classes
        Class<?> currentClass = classLoader.getClass();
        while ((currentClass = currentClass.getSuperclass()) != null) {
            try {
                Field field = currentClass.getDeclaredField("ucp");
                field.setAccessible(true);
                field.set(classLoader, urlClassPath);
            } catch (NoSuchFieldException e) {
                break;
            }
        }
    }

    public static boolean isApplicable(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }

        if (classLoader instanceof URLClassLoader) {
            return true;
        }

        Class<?> ucpOwner = classLoader.getClass();
        return ucpOwner.getName().startsWith("jdk.internal.loader.ClassLoaders$");
    }

    public static class ExtraURLClassPathMethodHandler implements MethodHandler {

        private ClassLoader watchResourceLoader;
        URL[] origClassPath;

        public ExtraURLClassPathMethodHandler(URL[] origClassPath) {
            this.origClassPath = origClassPath;
        }

        public ExtraURLClassPathMethodHandler(URL[] origClassPath, ClassLoader watchResourceLoader) {
            this.origClassPath = origClassPath;
            this.watchResourceLoader = watchResourceLoader;
        }

        /**
         * Return orig classpath as was set by hotswap agent.
         * Note: cannot use classLoader.getURLs(), because Tomcat WebappClassLoader does not return modified classPath.
         */
        public URL[] getOrigClassPath() {
            return origClassPath;
        }

        // code here with the implementation of MyCustomInterface
        // handling the entity and your customField
        public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();

            if ("findResource".equals(methodName) && parameterTypes.length == 2 &&
                    parameterTypes[0] == String.class &&
                    (parameterTypes[1] == Boolean.TYPE || parameterTypes[1] == Boolean.class)) {
                if (watchResourceLoader != null) {
                    URL resource = watchResourceLoader.getResource((String) args[0]);
                    if (resource != null) {
                        return resource;
                    }
                }
            } else if ("findResources".equals(methodName) && parameterTypes.length == 2 &&
                    parameterTypes[0] == String.class &&
                    (parameterTypes[1] == Boolean.TYPE || parameterTypes[1] == Boolean.class)) {
                if (watchResourceLoader != null) {
                    try {
                        Enumeration<URL> resources = watchResourceLoader.getResources((String) args[0]);
                        if (resources != null && resources.hasMoreElements()) {
                            return resources;
                        }
                    } catch (IOException e) {
                        LOGGER.debug("Unable to load resource {}", e, (String) args[0]);
                    }
                }
            }

            return proceed.invoke(self, args);
        }

    }

}
