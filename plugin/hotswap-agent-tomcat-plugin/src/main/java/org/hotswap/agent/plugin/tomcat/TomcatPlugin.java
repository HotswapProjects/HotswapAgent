package org.hotswap.agent.plugin.tomcat;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Catalina servlet container support.
 *
 * <p/>
 * <p>Plugin</p><ul>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Tomcat", description = "Catalina based servlet containers.",
        testedVersions = {"7.0.50"},
        expectedVersions = {"6x","7x", "8x"},
        supportClass={WebappLoaderTransformer.class}
)
public class TomcatPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(TomcatPlugin.class);

    private static final String WEB_INF_CLASSES = "/WEB-INF/classes/";

    // resolved tomcat version (6/7/8).
    int tomcatMajorVersion = 8;

    // tomcat associated resource object to a web application classloader
    static Map<Object, ClassLoader> registeredResourcesMap = new HashMap<Object, ClassLoader>();

    /**
     * Init the plugin during WebappLoader.start lifecycle. This method is invoked before the plugin is initialized.
     * @param appClassLoader main tomcat classloader for the webapp (in creation process). Only standard WebappClassLoader is supported.
     * @param resource tomcat resource associated to the classloader.
     */
    public static void init(ClassLoader appClassLoader, Object resource) {
        String version = resolveTomcatVersion(appClassLoader);
        int majorVersion = resolveTomcatMajorVersion(version);

        if (appClassLoader.getClass().getName().equals("org.apache.catalina.loader.WebappClassLoader")) {
            registeredResourcesMap.put(resource, appClassLoader);

            // create plugin configuration in advance to get extraClasspath and watchResources properties
            PluginConfiguration pluginConfiguration = new PluginConfiguration(appClassLoader);

            WatchResourcesClassLoader watchResourcesClassLoader = new WatchResourcesClassLoader(false);

            URL[] extraClasspath = pluginConfiguration.getExtraClasspath();
            if (extraClasspath.length > 0) {
                if (majorVersion > 7)
                    watchResourcesClassLoader.initExtraPath(extraClasspath);
                else
                    addRepositoriesAtStart(appClassLoader, extraClasspath, false);

            }

            URL[] watchResources = pluginConfiguration.getWatchResources();
            if (watchResources.length > 0) {
                if (majorVersion > 7)
                    watchResourcesClassLoader.initWatchResources(watchResources, PluginManager.getInstance().getWatcher());
                else
                    addRepositoriesAtStart(appClassLoader, watchResources, true);
            }

            // register special repo
            getExtraRepositories(appClassLoader).put(WEB_INF_CLASSES, watchResourcesClassLoader);

            // register special repo for webappDir
            URL webappDir = pluginConfiguration.getWebappDir();
            if (webappDir != null) {
                WatchResourcesClassLoader webappDirClassLoader = new WatchResourcesClassLoader(false);
                webappDirClassLoader.initExtraPath(new URL[] {webappDir});

                getExtraRepositories(appClassLoader).put("/", webappDirClassLoader);
            }
        }

        Object plugin = PluginManagerInvoker.callInitializePlugin(TomcatPlugin.class, appClassLoader);
        if (plugin != null) {
            ReflectionHelper.invoke(plugin, plugin.getClass(), "init", new Class[]{String.class}, version);
        } else {
            LOGGER.debug("TomcatPlugin is disabled in {}", appClassLoader);
        }
    }

    /**
     * Init plugin and resolve major version
     * @param version tomcat version string
     */
    private void init(String version) {
        LOGGER.info("Tomcat plugin initialized - Tomcat version '{}'", version);
        tomcatMajorVersion = resolveTomcatMajorVersion(version);
    }


    // for each app classloader map of tomcat repository name to associated watch resource classloader
    private static Map<ClassLoader, Map<String, ClassLoader>> extraRepositories = new HashMap<ClassLoader,  Map<String, ClassLoader>>();

    private static void addRepositoriesAtStart(ClassLoader appClassLoader, URL[] newRepositories, boolean watchResources) {
        String[] currentRepositories = (String[]) ReflectionHelper.get(appClassLoader, "repositories");
        String[] repositories = new String[currentRepositories.length + newRepositories.length];

        for (int i=0; i < newRepositories.length; i++) {
            repositories[i] = "extraClasspath:" + newRepositories[i].toString();
        }
        for (int i = 0; i < currentRepositories.length; i++) {
            repositories[i+newRepositories.length] = currentRepositories[i];
        }
        ReflectionHelper.set(appClassLoader, appClassLoader.getClass(), "repositories", repositories);

        File[] files = (File[]) ReflectionHelper.get(appClassLoader, "files");
        File[] result2 = new File[files.length + newRepositories.length];
        for (int i=0; i < newRepositories.length; i++) {
            try {
                WatchResourcesClassLoader watchResourcesClassLoader = new WatchResourcesClassLoader();

                if (watchResources) {
                    watchResourcesClassLoader.initWatchResources(new URL[]{newRepositories[i]}, PluginManager.getInstance().getWatcher());
                } else {
                    watchResourcesClassLoader.initExtraPath(new URL[]{newRepositories[i]});
                }

                getExtraRepositories(appClassLoader).put(repositories[i], watchResourcesClassLoader);
                result2[i] = new File(newRepositories[i].toURI());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < files.length; i++) {
            result2[i+newRepositories.length] = files[i];
        }
        ReflectionHelper.set(appClassLoader, appClassLoader.getClass(), "files", result2);
    }

    private static Map<String, ClassLoader> getExtraRepositories(ClassLoader appClassLoader) {
        if (!extraRepositories.containsKey(appClassLoader)) {
            extraRepositories.put(appClassLoader, new HashMap<String, ClassLoader>());
        }
        return extraRepositories.get(appClassLoader);
    }


    public static InputStream getExtraResource(Object resource, String name) {
        URL url = getExtraResource0(resource, name);
        if (url != null) {
            try {
                return url.openStream();
            } catch (IOException e) {
                LOGGER.error("Unable to open stream from URL {}", e, url);
            }
        }

        return null;
    }

    public static File getExtraResourceFile(Object resource, String name) {
        ClassLoader appClassLoader = registeredResourcesMap.get(resource);
        if (appClassLoader == null)
            return null;

        Map<String, ClassLoader> classLoaderExtraRepositories = getExtraRepositories(appClassLoader);
        if (name.startsWith(WEB_INF_CLASSES) && classLoaderExtraRepositories.containsKey(WEB_INF_CLASSES)) {
            // strip of leading /WEB-INF/classes/ and search for the resources
            String resourceName = name.substring(WEB_INF_CLASSES.length());
            URL url = classLoaderExtraRepositories.get(WEB_INF_CLASSES).getResource(resourceName);
            if (url != null) {
                try {
                    return new File(url.toURI());
                } catch (Exception e) {
                    LOGGER.error("Unable to open stream from URL {}", e, url);
                }
            }
        } else if (classLoaderExtraRepositories.containsKey("/")) {
            URL url = classLoaderExtraRepositories.get("/").getResource(name.substring(1)); // strip off leading "/"
            if (url != null) {
                try {
                    return new File(url.toURI());
                } catch (Exception e) {
                    LOGGER.error("Unable to open stream from URL {}", e, url);
                }
            }
        }

        return null;
    }

    public static long getExtraResourceLength(Object resource, String name) {
        URL url = getExtraResource0(resource, name);
        if (url != null) {
            try {
                return new File(url.toURI()).length();
            } catch (Exception e) {
                LOGGER.error("Unable to open file at URL {}", e, url);
            }
        }

        return 0;
    }

    private static URL getExtraResource0(Object resource, String name) {
        ClassLoader appClassLoader = registeredResourcesMap.get(resource);
        if (appClassLoader == null)
            return null;

        for (Map.Entry<String, ClassLoader> repo : getExtraRepositories(appClassLoader).entrySet()) {
            if (name.startsWith(repo.getKey())) {
                String resourceName = name.substring(repo.getKey().length());
                // return from associated classloader
                return repo.getValue().getResource(resourceName);
            }
        }

        return null;
    }


    /**
     * Resolve the server version from ServerInfo class.
     * @param appClassLoader application classloader
     * @return the server info String
     */
    private static String resolveTomcatVersion(ClassLoader appClassLoader) {
        try {
            Class serverInfo = appClassLoader.loadClass("org.apache.catalina.util.ServerInfo");
            return (String) ReflectionHelper.invoke(null, serverInfo, "getServerNumber", new Class[]{});
        } catch (Exception e) {
            LOGGER.debug("Unable to resolve server version", e);
            return "unknown";
        }
    }

    /**
     * Try to resolve version string from the version.
     */
    private static int resolveTomcatMajorVersion(String version) {
        try {
            return Integer.valueOf(version.substring(0, 1));
        } catch (Exception e) {
            LOGGER.debug("Unable to resolve server main version from version string {}", e, version);
            // assume latest known version
            return 8;
        }
    }

}
