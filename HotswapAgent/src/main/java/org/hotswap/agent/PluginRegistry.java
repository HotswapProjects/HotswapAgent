package org.hotswap.agent;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.handler.AnnotationProcessor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.ClassLoaderPatcher;
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

    // plugin class -> Map (ClassLoader -> Plugin instance)
    protected Map<Class, Map<ClassLoader, Object>> registeredPlugins = Collections.synchronizedMap(new HashMap<Class, Map<ClassLoader, Object>>());

    /**
     * Returns map of all registered plugins.
     *
     * @return map plugin class -> Map (ClassLoader -> Plugin instance)
     */
    public Map<Class, Map<ClassLoader, Object>> getRegisteredPlugins() {
        return registeredPlugins;
    }

    // plugin manager instance
    private PluginManager pluginManager;

    // scanner to search for plugins
    private ClassPathAnnotationScanner annotationScanner;

    public void setAnnotationScanner(ClassPathAnnotationScanner annotationScanner) {
        this.annotationScanner = annotationScanner;
    }

    // processor to resolve plugin annotations
    protected AnnotationProcessor annotationProcessor;

    public void setAnnotationProcessor(AnnotationProcessor annotationProcessor) {
        this.annotationProcessor = annotationProcessor;
    }

    // copy plugin classes from application classloader to the agent classloader
    private ClassLoaderPatcher classLoaderPatcher;

    public void setClassLoaderPatcher(ClassLoaderPatcher classLoaderPatcher) {
        this.classLoaderPatcher = classLoaderPatcher;
    }

    /**
     * Create an instanec of plugin registry and initialize scanner and processor.
     */
    public PluginRegistry(PluginManager pluginManager, ClassLoaderPatcher classLoaderPatcher) {
        this.pluginManager = pluginManager;
        this.classLoaderPatcher = classLoaderPatcher;
        annotationScanner = new ClassPathAnnotationScanner(Plugin.class.getName(), new ClassPathScanner());
        annotationProcessor = new AnnotationProcessor(pluginManager);
    }

    /**
     * Scan for plugins by @Plugin annotation on PLUGIN_PATH and process plugin annotations.
     *
     * @param classLoader   classloader to resolve plugin package. This will be used by annotation scanner.
     * @param pluginPackage the package to be searched (e.g. org.agent.hotswap.plugin)
     */
    public void scanPlugins(ClassLoader classLoader, String pluginPackage) {
        String pluginPath = pluginPackage.replace(".", "/");
        ClassLoader agentClassLoader = getClass().getClassLoader();

        try {
            List<String> discoveredPlugins = annotationScanner.scanPlugins(classLoader, pluginPath);
            LOGGER.info("Discovered plugins: " + Arrays.toString(discoveredPlugins.toArray()));

            // Plugin class must be always defined directly in the agent classloader, otherwise it will not be available
            // to the instrumentation process. Copy the definition using patcher
            if (discoveredPlugins.size() > 0 && agentClassLoader != classLoader) {
                classLoaderPatcher.patch(classLoader, pluginPath, agentClassLoader, null);
            }

            for (String discoveredPlugin : discoveredPlugins) {
                Class pluginClass = Class.forName(discoveredPlugin, true, agentClassLoader);

                // check for duplicate plugin definition. It may happen if class directory AND the JAR file
                // are both available.
                if (registeredPlugins.containsKey(pluginClass))
                    continue;

                registeredPlugins.put(pluginClass, Collections.synchronizedMap(new HashMap<ClassLoader, Object>()));

                if (annotationProcessor.processAnnotations(pluginClass)) {
                    LOGGER.debug("Plugin registered {}.", pluginClass);
                } else {
                    LOGGER.error("Error processing annotations for plugin {}. Plugin was unregistered.", pluginClass);
                    registeredPlugins.remove(pluginClass);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in plugin initial processing for plugin package '{}'", e, pluginPackage);
        }
    }

    /**
     * Init a plugin (create new plugin instance) in a application classloader.
     * Each classloader may contain only one instance of a plugin.
     *
     * @param pluginClass    class of plugin to instantiate
     * @param appClassLoader target application classloader
     * @return the new plugin instance
     */
    public Object initializePlugin(String pluginClass, ClassLoader appClassLoader) {
        // ensure classloader initialized
        pluginManager.initClassLoader(appClassLoader);

        Class<Object> clazz = getPluginClass(pluginClass);

        // already initialized in this or parent classloader
        if (hasPlugin(clazz, appClassLoader))
            return getPlugin(clazz, appClassLoader);

        Object pluginInstance = instantiate(clazz);
        registeredPlugins.get(clazz).put(appClassLoader, pluginInstance);

        if (annotationProcessor.processAnnotations(pluginInstance)) {
            LOGGER.info("Plugin '{}' initialized in ClassLoader '{}'.", pluginClass, appClassLoader);
        } else {
            LOGGER.error("Plugin '{}' NOT initialized in ClassLoader '{}', error while processing annotations.", pluginClass, appClassLoader);
            registeredPlugins.get(clazz).remove(appClassLoader);
        }

        return pluginInstance;
    }

    /**
     * Returns plugin instance by it's type and classLoader.
     *
     * @param pluginClass type of the plugin
     * @param classLoader classloader of the plugin
     * @param <T>         type of the plugin to return correct instance.
     * @return the plugin
     * @throws IllegalArgumentException if classLoader not initialized or plugin not found
     */
    public <T> T getPlugin(Class<T> pluginClass, ClassLoader classLoader) {
        if (registeredPlugins.isEmpty()) {
            throw new IllegalStateException("No plugin initialized. " +
                    "The Hotswap Agent JAR must NOT be in app classloader (only registered as --javaagent: startup parameter). " +
                    "Please check your mapPreviousState.");
        }

        if (!registeredPlugins.containsKey(pluginClass))
            throw new IllegalArgumentException(String.format("Plugin %s is not known to the registry.", pluginClass));

        for (Map.Entry<ClassLoader, Object> registeredClassLoaderEntry : registeredPlugins.get(pluginClass).entrySet()) {
            if (isParentClassLoader(registeredClassLoaderEntry.getKey(), classLoader)) {
                //noinspection unchecked
                return (T) registeredClassLoaderEntry.getValue();
            }
        }

        // not found
        throw new IllegalArgumentException(String.format("Plugin %s is not initialized in classloader %s.", pluginClass, classLoader));
    }

    /**
     * Check if plugin is initialized in classLoader.
     *
     * @param pluginClass type of the plugin
     * @param classLoader classloader of the plugin
     * @return true/false
     */
    public boolean hasPlugin(Class<?> pluginClass, ClassLoader classLoader) {
        if (!registeredPlugins.containsKey(pluginClass))
            return false;

        for (Map.Entry<ClassLoader, Object> registeredClassLoaderEntry : registeredPlugins.get(pluginClass).entrySet()) {
            if (isParentClassLoader(registeredClassLoaderEntry.getKey(), classLoader)) {
                return true;
            }
        }
        return false;
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
    private Class<Object> getPluginClass(String pluginClass) {
        try {
            // noinspection unchecked
            return (Class<Object>) getClass().getClassLoader().loadClass(pluginClass);
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
}
