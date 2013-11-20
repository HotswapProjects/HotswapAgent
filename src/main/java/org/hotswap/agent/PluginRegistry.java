package org.hotswap.agent;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationProcessor;
import org.hotswap.agent.util.scanner.ClassPathAnnotationScanner;
import org.hotswap.agent.util.scanner.ClassPathScanner;

import java.util.*;

/**
 * Registry to support plugin manager.
 *
 * @author Jiri Bubnik
 */
public class PluginRegistry {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginRegistry.class);

    public static final String PLUGIN_PATH = "org/hotswap/agent/plugin";

    // plugin class -> Map (ClassLoader -> Plugin instance)
    protected Map<Class, Map<ClassLoader, Object>> registeredPlugins = Collections.synchronizedMap(new HashMap<Class, Map<ClassLoader, Object>>());

    private PluginManager pluginManager;

    private ClassPathAnnotationScanner annotationScanner;

    public void setAnnotationScanner(ClassPathAnnotationScanner annotationScanner) {
        this.annotationScanner = annotationScanner;
    }

    protected AnnotationProcessor annotationProcessor;

    public void setAnnotationProcessor(AnnotationProcessor annotationProcessor) {
        this.annotationProcessor = annotationProcessor;
    }


    public PluginRegistry(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        annotationScanner = new ClassPathAnnotationScanner(Plugin.class.getName(), new ClassPathScanner());
        annotationProcessor = new AnnotationProcessor(pluginManager);
    }

    public void scanPlugins() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            List<String> discoveredPlugins = annotationScanner.scanPlugins(PLUGIN_PATH);
            LOGGER.info("Discovered plugins: " + Arrays.toString(discoveredPlugins.toArray()));

            for (String discoveredPlugin : discoveredPlugins) {
                Class pluginClass = Class.forName(discoveredPlugin, true, classLoader);
                if (annotationProcessor.processAnnotations(pluginClass)) {
                    LOGGER.debug("Plugin registered: " + pluginClass);
                    registeredPlugins.put(pluginClass, Collections.synchronizedMap(new HashMap<ClassLoader, Object>()));
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in plugin initial processing for path '{}'", e, PLUGIN_PATH);
        }
    }

    public void initializePlugin(String pluginClass, ClassLoader appClassLoader) {
        // ensure classloader initialized
        pluginManager.initClassLoader(appClassLoader);

        Class clazz = getPluginClass(pluginClass);

        // already initialized in this or parent classloader
        if (getPlugin(clazz, appClassLoader) != null)
            return;

        Object pluginInstance = instantiate(clazz);
        registeredPlugins.get(clazz).put(appClassLoader, pluginInstance);

        if (annotationProcessor.processAnnotations(pluginInstance)) {
            LOGGER.info("Plugin '{}' initialized in ClassLoader '{}'.", pluginClass, appClassLoader);
        } else {
            registeredPlugins.get(clazz).remove(appClassLoader);
        }
    }

    /**
     * Returns plugin instance by it's type and classLoader.
     *
     * @param pluginClass type of the plugin
     * @param classLoader classloader of the plugin
     * @param <T>         type of the plugin to return correct instance.
     * @return the plugin or null if not found.
     */
    public <T> T getPlugin(Class<T> pluginClass, ClassLoader classLoader) {
        if (registeredPlugins.isEmpty()) {
            throw new IllegalStateException("No plugin initialized. " +
                    "The Hotswap Agent JAR must NOT be in app classloader (only registered as --javaagent: startup parameter). " +
                    "Please check your mapPreviousState.");
        }

        if (!registeredPlugins.containsKey(pluginClass))
            throw new IllegalArgumentException(String.format("Plugin '%s' is not a known to the registry.", pluginClass));

        for (Map.Entry<ClassLoader, Object> registeredClassLoaderEntry : registeredPlugins.get(pluginClass).entrySet()) {
            if (isParentClassLoader(registeredClassLoaderEntry.getKey(), classLoader)) {
                //noinspection unchecked
                return (T) registeredClassLoaderEntry.getValue();
            }
        }

        // not found
        return null;
    }

    /**
     * Search for the plugin in the registry and return associated classloader.
     *
     * @param plugin existing plugin
     * @return the classloader this plugin is associated with
     */
    public ClassLoader getAppClassLoader(Object plugin) {
        // search with for loop. Maybe performance improvement to create reverse map if this is used heavily
        for (Map<ClassLoader, Object> plugins : registeredPlugins.values()) {
            for (Map.Entry<ClassLoader, Object> entry : plugins.entrySet()) {
                if (entry.getValue().equals(plugin))
                    return entry.getKey();
            }
        }

        throw new IllegalArgumentException("Plugin not found in the registry " + plugin);
    }


    // resolve class in this classloader - plugin class should be always only in the same classloader
    // as the plugin manager.
    private Class getPluginClass(String pluginClass) {
        try {
            return getClass().getClassLoader().loadClass(pluginClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Plugin class not found " + pluginClass, e);
        }
    }

    // check if parentClassLoader is parent of classLoader
    private boolean isParentClassLoader(ClassLoader parentClassLoader, ClassLoader classLoader) {
        if (parentClassLoader.equals(classLoader))
            return true;
        else if (classLoader.getParent() != null)
            return isParentClassLoader(parentClassLoader, classLoader.getParent());
        else
            return false;
    }

    /**
     * Create a new instance of the plugin.
     *
     * @param plugin plugin class
     * @return new instance or null if instantiation fail.
     */
    protected Object instantiate(Class<Object> plugin) {
        try {
            return plugin.newInstance();
        } catch (InstantiationException e) {
            LOGGER.error("Error instantiating plugin: " + plugin.getClass().getName(), e);
        } catch (IllegalAccessException e) {
            LOGGER.error("Plugin: " + plugin.getClass().getName()
                    + " does not contain public no param constructor", e);
        }
        return null;
    }

    public Map<Class, Map<ClassLoader, Object>> getRegisteredPlugins() {
        return registeredPlugins;
    }
}
