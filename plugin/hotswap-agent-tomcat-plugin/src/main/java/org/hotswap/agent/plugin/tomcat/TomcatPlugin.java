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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        expectedVersions = {"6x","7x", "8x", "9.x"},
        supportClass = {WebappLoaderTransformer.class}
)
public class TomcatPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(TomcatPlugin.class);

    // Supported class loaders extended from WebAppClassLoader
    private static final String TOMCAT_WEBAPP_CLASS_LOADER = "org.apache.catalina.loader.WebappClassLoader";
    private static final String TOMCAT_PARALLEL_WEBAPP_CLASS_LOADER = "org.apache.catalina.loader.ParallelWebappClassLoader";
    private static final String GLASSFISH_WEBAPP_CLASS_LOADER = "org.glassfish.web.loader.WebappClassLoader";
    private static final String TOMEE_WEBAPP_CLASS_LOADER = "org.apache.tomee.catalina.TomEEWebappClassLoader";
    private static final String TOMCAT_EMBEDDED_WEBAPP_CLASS_LOADER = "org.springframework.boot.web.embedded.tomcat.TomcatEmbeddedWebappClassLoader";

    static final Set<String> supportedClassLoaders;

    static {
        supportedClassLoaders = new HashSet<>();
        supportedClassLoaders.add(TOMCAT_WEBAPP_CLASS_LOADER);
        supportedClassLoaders.add(TOMCAT_PARALLEL_WEBAPP_CLASS_LOADER);
        supportedClassLoaders.add(GLASSFISH_WEBAPP_CLASS_LOADER);
        supportedClassLoaders.add(TOMEE_WEBAPP_CLASS_LOADER);
        supportedClassLoaders.add(TOMCAT_EMBEDDED_WEBAPP_CLASS_LOADER);
    }

    private static final String WEB_INF_CLASSES = "/WEB-INF/classes/";

    // resolved tomcat version (6/7/8).
    int tomcatMajorVersion = 8;

    // tomcat associated resource object to a web application classloader
    static Map<Object, ClassLoader> registeredResourcesMap = new HashMap<>();

    // for each app classloader map of tomcat repository name to associated watch resource classloader
    private static Map<ClassLoader, Map<String, ClassLoader>> extraRepositories = new HashMap<>();

    /**
     * Init the plugin during WebappLoader.start lifecycle. This method is invoked before the plugin is initialized.
     * @param appClassLoader main tomcat classloader for the webapp (in creation process). Only standard WebappClassLoader is supported.
     * @param resource tomcat resource associated to the classloader.
     */
    public static void init(ClassLoader appClassLoader, Object resource) {
        String version = resolveTomcatVersion(appClassLoader);
        int majorVersion = resolveTomcatMajorVersion(version);

        Object plugin = PluginManagerInvoker.callInitializePlugin(TomcatPlugin.class, appClassLoader);
        if (plugin != null) {
            ReflectionHelper.invoke(plugin, plugin.getClass(), "init", new Class[]{String.class, ClassLoader.class},
                    version, appClassLoader);
        } else {
            LOGGER.debug("TomcatPlugin is disabled in {}", appClassLoader);
            return;
        }

        if (supportedClassLoaders.contains(appClassLoader.getClass().getName())) {
            registeredResourcesMap.put(resource, appClassLoader);

            // find plugin configuration in advance to get extraClasspath and watchResources properties
            PluginConfiguration pluginConfiguration = PluginManager.getInstance().getPluginConfiguration(appClassLoader);

            WatchResourcesClassLoader watchResourcesClassLoader = new WatchResourcesClassLoader(false);

            URL[] extraClasspath = pluginConfiguration.getExtraClasspath();
            if (extraClasspath.length > 0) {
                if (majorVersion > 7) {
                    watchResourcesClassLoader.initExtraPath(extraClasspath);
                } else {
                    addRepositoriesAtStart(appClassLoader, extraClasspath, false);
                }
            }

            URL[] watchResources = pluginConfiguration.getWatchResources();
            if (watchResources.length > 0) {
                if (majorVersion > 7) {
                    watchResourcesClassLoader.initWatchResources(watchResources, PluginManager.getInstance().getWatcher());
                } else {
                    addRepositoriesAtStart(appClassLoader, watchResources, true);
                }
            }

            // register special repo
            getExtraRepositories(appClassLoader).put(WEB_INF_CLASSES, watchResourcesClassLoader);

            URL[] webappDir = pluginConfiguration.getWebappDir();
            if (webappDir.length > 0) {
                for (URL url : webappDir) {
                    LOGGER.debug("Watching 'webappDir' for changes: {}", url);
                }
                WatchResourcesClassLoader webappDirClassLoader = new WatchResourcesClassLoader(false);
                webappDirClassLoader.initExtraPath(webappDir);

                getExtraRepositories(appClassLoader).put("/", webappDirClassLoader);
            }
        }
    }

    /**
     * Init plugin and resolve major version.
     *
     * @param version tomcat version string
     * @param classLoader the class loader
     */
    private void init(String version, ClassLoader appClassLoader) {
        if (appClassLoader.getClass().getName().equals(GLASSFISH_WEBAPP_CLASS_LOADER)) {
            LOGGER.info("Tomcat plugin initialized - GlassFish embedded Tomcat version '{}'", version);
        } else {
            LOGGER.info("Tomcat plugin initialized - Tomcat version '{}'", version);
        }
        tomcatMajorVersion = resolveTomcatMajorVersion(version);
    }

    public static void close(ClassLoader classLoader) {
        Map<String, ClassLoader> registerMap = extraRepositories.remove(classLoader);
        if (registerMap != null) {
            for (ClassLoader loader : registerMap.values()) {
                PluginManager.getInstance().getWatcher().closeClassLoader(loader);
            }
        }
    }

    private static void addRepositoriesAtStart(ClassLoader appClassLoader, URL[] newRepositories, boolean watchResources) {

        String[] currentRepositories = (String[]) ReflectionHelper.get(appClassLoader, "repositories");
        String[] repositories = new String[currentRepositories.length + newRepositories.length];

        for (int i = 0; i < newRepositories.length; i++) {
            repositories[i] = "extraClasspath:" + newRepositories[i].toString();
        }
        for (int i = 0; i < currentRepositories.length; i++) {
            repositories[i + newRepositories.length] = currentRepositories[i];
        }

        // since 7.0.65, repositories and files are moved to super class.
        ReflectionHelper.set(appClassLoader, "repositories", repositories);

        File[] files = (File[]) ReflectionHelper.get(appClassLoader, "files");
        File[] result2 = new File[files.length + newRepositories.length];
        for (int i = 0; i < newRepositories.length; i++) {
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
            result2[i + newRepositories.length] = files[i];
        }
        ReflectionHelper.set(appClassLoader, "files", result2);
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
                // since 7.0.65, the resource name is always starting with "/", trim it otherwise
                // WatchResourcesClassLoader doesn't work
                if (resourceName.startsWith("/")) {
                    resourceName = resourceName.substring(1);
                }
                // return from associated classloader
                return repo.getValue().getResource(resourceName);
            }
        }

        return null;
    }


    /**
     * Resolve the server version from ServerInfo class.
     *
     * @param appClassLoader application classloader
     * @return the server info String
     */
    private static String resolveTomcatVersion(ClassLoader appClassLoader) {
        try {
            Class serverInfo = appClassLoader.loadClass("org.apache.catalina.util.ServerInfo");
            if (appClassLoader.getClass().getName().equals(GLASSFISH_WEBAPP_CLASS_LOADER)) {
                return (String) ReflectionHelper.invoke(null, serverInfo, "getPublicServerInfo", new Class[]{});
            }
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
