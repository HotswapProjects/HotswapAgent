package org.hotswap.agent.util.classloader;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;

import org.hotswap.agent.javassist.util.proxy.MethodFilter;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.javassist.util.proxy.ProxyObject;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Helper methods to enhance URL ClassLoader.
 */
public class URLClassLoaderHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(URLClassLoaderHelper.class);

    private static Class<?> urlClassPathProxyClass = null;

    static {
        Class<?> urlClassPathClass = null;
        try {
            urlClassPathClass = URLClassLoaderHelper.class.getClassLoader().loadClass("sun.misc.URLClassPath");
        } catch (ClassNotFoundException e) {
            try {
                // java9
                urlClassPathClass = URLClassLoaderHelper.class.getClassLoader().loadClass("jdk.internal.loader.URLClassPath");
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
     * @param classLoader    URL classloader
     * @param extraClassPath path to prepend
     */
    public static void prependClassPath(URLClassLoader classLoader, URL[] extraClassPath) {
	// TomcatPlugin já faz tudo que é necessário
	if (classLoader.getClass().getSuperclass().getName().equals("org.apache.catalina.loader.WebappClassLoaderBase"))
	    return;

        synchronized (classLoader) {

            // set new URLClassPath to the classloader via reflection
            try {
                Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
                ucpField.setAccessible(true);

                URL[] origClassPath = getOrigClassPath(classLoader, ucpField);

                URL[] modifiedClassPath = new URL[origClassPath.length + extraClassPath.length];
                System.arraycopy(extraClassPath, 0, modifiedClassPath, 0, extraClassPath.length);
                System.arraycopy(origClassPath, 0, modifiedClassPath, extraClassPath.length, origClassPath.length);

                Constructor<?> constr = urlClassPathProxyClass.getConstructor(new Class[] { URL[].class });
                Object urlClassPath = constr.newInstance(new Object[] { modifiedClassPath });

                ExtraURLClassPathMethodHandler methodHandler = new ExtraURLClassPathMethodHandler(modifiedClassPath);
                ((Proxy)urlClassPath).setHandler(methodHandler);

                ucpField.set(classLoader, urlClassPath);

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

                Constructor<?> constr = urlClassPathProxyClass.getConstructor(new Class[] { URL[].class });
                Object urlClassPath = constr.newInstance(new Object[] { origClassPath });

                ExtraURLClassPathMethodHandler methodHandler = new ExtraURLClassPathMethodHandler(origClassPath, watchResourceLoader);
                ((Proxy)urlClassPath).setHandler(methodHandler);

                ucpField.set(classLoader, urlClassPath);

                LOGGER.debug("WatchResourceLoader registered to classLoader {}", classLoader);
            } catch (Exception e) {
                LOGGER.debug("Unable to register WatchResourceLoader to classLoader {}", e, classLoader);
            }
        }
    }

    private static URL[] getOrigClassPath(URLClassLoader classLoader, Field ucpField) throws IllegalAccessException {
        URL[] origClassPath = null;
        Object urlClassPath = ucpField.get(classLoader);

        if (urlClassPath instanceof ProxyObject) {
            ProxyObject p = (ProxyObject) urlClassPath;
            MethodHandler handler = p.getHandler();

            if (handler instanceof ExtraURLClassPathMethodHandler) {
              origClassPath = ((ExtraURLClassPathMethodHandler)handler).getOrigClassPath();
            }

        } else {
            origClassPath = classLoader.getURLs();
        }
        return origClassPath;
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
                   parameterTypes[0] == String.class && parameterTypes[1] == Boolean.class) {
                  if (watchResourceLoader != null) {
                      URL resource = watchResourceLoader.getResource((String) args[0]);
                      if (resource != null) {
                          return resource;
                      }
                  }
              } else if ("findResources".equals(methodName) && parameterTypes.length == 2 &&
                      parameterTypes[0] == String.class && parameterTypes[1] == Boolean.class) {
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
