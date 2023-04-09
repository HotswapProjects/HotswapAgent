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
package org.hotswap.agent.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.handler.AnnotationProcessor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.ClassLoaderDefineClassPatcher;
import org.hotswap.agent.util.scanner.ClassPathAnnotationScanner;
import org.hotswap.agent.util.scanner.ClassPathScanner;

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
    private ClassLoaderDefineClassPatcher classLoaderPatcher;

    public void setClassLoaderPatcher(ClassLoaderDefineClassPatcher classLoaderPatcher) {
        this.classLoaderPatcher = classLoaderPatcher;
    }

    /**
     * Create an instanec of plugin registry and initialize scanner and processor.
     */
    public PluginRegistry(PluginManager pluginManager, ClassLoaderDefineClassPatcher classLoaderPatcher) {
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
            List<String> discoveredPluginNames = new ArrayList<>();

            // Plugin class must be always defined directly in the agent classloader, otherwise it will not be available
            // to the instrumentation process. Copy the definition using patcher
            if (discoveredPlugins.size() > 0 && agentClassLoader != classLoader) {
                classLoaderPatcher.patch(classLoader, pluginPath, agentClassLoader, null);
            }

            for (String discoveredPlugin : discoveredPlugins) {
                Class pluginClass = Class.forName(discoveredPlugin, true, agentClassLoader);
                Plugin pluginAnnotation = (Plugin) pluginClass.getAnnotation(Plugin.class);

                if (pluginAnnotation == null) {
                    LOGGER.error("Scanner discovered plugin class {} which does not contain @Plugin annotation.", pluginClass);
                    continue;
                }
                String pluginName = pluginAnnotation.name();

                if (HotswapAgent.isPluginDisabled(pluginName)) {
                    LOGGER.debug("Plugin {} is disabled, skipping...", pluginName);
                    continue;
                }

                // check for duplicate plugin definition. It may happen if class directory AND the JAR file
                // are both available.
                if (registeredPlugins.containsKey(pluginClass))
                    continue;

                registeredPlugins.put(pluginClass, Collections.synchronizedMap(new HashMap<ClassLoader, Object>()));

                if (annotationProcessor.processAnnotations(pluginClass, pluginClass)) {
                    LOGGER.debug("Plugin registered {}.", pluginClass);
                } else {
                    LOGGER.error("Error processing annotations for plugin {}. Plugin was unregistered.", pluginClass);
                    registeredPlugins.remove(pluginClass);
                }

                discoveredPluginNames.add(pluginName);
            }

            LOGGER.info("Discovered plugins: " + Arrays.toString(discoveredPluginNames.toArray()));

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
     * @return the new plugin instance or null if plugin is disabled.
     */
    public Object initializePlugin(String pluginClass, ClassLoader appClassLoader) {
        if (appClassLoader == null)
            throw new IllegalArgumentException("Cannot initialize plugin '" + pluginClass + "', appClassLoader is null.");

        // ensure classloader initialized
        pluginManager.initClassLoader(appClassLoader);

        Class<Object> clazz = getPluginClass(pluginClass);

        // skip if the plugin is disabled
        if (pluginManager.getPluginConfiguration(appClassLoader).isDisabledPlugin(clazz)) {
            LOGGER.debug("Plugin {} disabled in classloader {}.", clazz, appClassLoader );
            return null;
        }

        // already initialized in this or parent classloader
        if (doHasPlugin(clazz, appClassLoader, false, true)) {
            LOGGER.debug("Plugin {} already initialized in parent classloader of {}.", clazz, appClassLoader );
            return getPlugin(clazz, appClassLoader);
        }

        Object pluginInstance = registeredPlugins.get(clazz).get(appClassLoader);

        if (annotationProcessor.processAnnotations(pluginInstance)) {
            LOGGER.info("Plugin '{}' initialized in ClassLoader '{}'.", pluginClass, appClassLoader);
        } else {
            LOGGER.error("Plugin '{}' NOT initialized in ClassLoader '{}', error while processing annotations.", pluginClass, appClassLoader);
            registeredPlugins.get(clazz).remove(appClassLoader);
        }

        return pluginInstance;
    }

    public void initializePluginInstance(Object pluginInstance) {
        registeredPlugins.put(pluginInstance.getClass(),
                Collections.singletonMap(pluginInstance.getClass().getClassLoader(), pluginInstance));
        if (!annotationProcessor.processAnnotations(pluginInstance)) {
            throw new IllegalStateException("Unable to initialize plugin");
        }

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

        Map<ClassLoader, Object> pluginInstances = registeredPlugins.get(pluginClass);
        synchronized(pluginInstances) {
            for (Map.Entry<ClassLoader, Object> registeredClassLoaderEntry : pluginInstances.entrySet()) {
                if (isParentClassLoader(registeredClassLoaderEntry.getKey(), classLoader)) {
                    //noinspection unchecked
                    return (T) registeredClassLoaderEntry.getValue();
                }
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
     * @param checkParent for parent classloaders as well?
     * @return true/false
     */
    public boolean hasPlugin(Class<?> pluginClass, ClassLoader classLoader, boolean checkParent) {
        return doHasPlugin(pluginClass, classLoader,checkParent, false);
    }

    public boolean doHasPlugin(Class<?> pluginClass, ClassLoader classLoader, boolean checkParent, boolean createIfMissing) {
        if (!registeredPlugins.containsKey(pluginClass))
            return false;

        Map<ClassLoader, Object> pluginInstances = registeredPlugins.get(pluginClass);
        synchronized (pluginInstances) {
            for (Map.Entry<ClassLoader, Object> registeredClassLoaderEntry : pluginInstances.entrySet()) {
                if (checkParent && isParentClassLoader(registeredClassLoaderEntry.getKey(), classLoader)) {
                    return true;
                } else if (registeredClassLoaderEntry.getKey().equals(classLoader)) {
                    return true;
                }
            }
            if (createIfMissing) {
                Object pluginInstance = instantiate((Class<Object>) pluginClass);
                pluginInstances.put(classLoader, pluginInstance);
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
        Class<Object> clazz = getPluginClass(plugin.getClass().getName());
        Map<ClassLoader, Object> pluginInstances = registeredPlugins.get(clazz);
        if (pluginInstances != null) {
            synchronized(pluginInstances) {
                for (Map.Entry<ClassLoader, Object> entry : pluginInstances.entrySet()) {
                    if (entry.getValue().equals(plugin))
                        return entry.getKey();
                }
            }
        }
        throw new IllegalArgumentException("Plugin not found in the registry " + plugin);
    }


    // resolve class in this classloader - plugin class should be always only in the same classloader
    // as the plugin manager.
    protected Class<Object> getPluginClass(String pluginClass) {
        try {
            // noinspection unchecked
            if (getClass().getClassLoader() == null) {
                return (Class<Object>) Class.forName(pluginClass, true, null);
            }
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

    /**
     * Remove all registered plugins for a classloader.
     * @param classLoader classloader to cleanup
     */
    public void closeClassLoader(ClassLoader classLoader) {
        LOGGER.debug("Closing classloader {}.", classLoader);
        synchronized (registeredPlugins) {
            for (Map<ClassLoader, Object> plugins : registeredPlugins.values()) {
                plugins.remove(classLoader);
            }
        }
    }
}
