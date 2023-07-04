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

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.impl.SchedulerImpl;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.classloader.ClassLoaderDefineClassPatcher;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.WatcherFactory;

/**
 * The main agent plugin manager, well known singleton controller.
 *
 * @author Jiri Bubnik
 */
public class PluginManager {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginManager.class);

    public static final String PLUGIN_PACKAGE = "org.hotswap.agent.plugin";

    //////////////////////////   MANAGER SINGLETON /////////////////////////////////////

    // singleton instance
    private static PluginManager INSTANCE = new PluginManager();

    /**
     * Get the singleton instance of the plugin manager.
     */
    public static PluginManager getInstance() {
        return INSTANCE;
    }

    // ensure singleton
    private PluginManager() {
        hotswapTransformer = new HotswapTransformer();
        pluginRegistry = new PluginRegistry(this, classLoaderPatcher);
    }

    // the instrumentation API
    private Instrumentation instrumentation;

    private Object hotswapLock = new Object();

    //////////////////////////   PLUGINS /////////////////////////////////////

    /**
     * Returns a plugin instance by its type and classLoader.
     *
     * @param clazz       type name of the plugin (IllegalArgumentException class is not known to the classLoader)
     * @param classLoader classloader of the plugin
     * @return plugin instance or null if not found
     */
    public Object getPlugin(String clazz, ClassLoader classLoader) {
        try {
            return getPlugin(Class.forName(clazz), classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Plugin class not found " + clazz, e);
        }
    }

    /**
     * Returns a plugin instance by its type and classLoader.
     *
     * @param clazz       type of the plugin
     * @param classLoader classloader of the plugin
     * @param <T>         type of the plugin to return correct instance.
     * @return the plugin or null if not found.
     */
    public <T> T getPlugin(Class<T> clazz, ClassLoader classLoader) {
        return pluginRegistry.getPlugin(clazz, classLoader);
    }

    /**
     * Check if plugin is initialized in classLoader.
     *
     * @param pluginClassName type of the plugin
     * @param classLoader classloader of the plugin
     * @return true/false
     */
    public boolean isPluginInitialized(String pluginClassName, ClassLoader classLoader) {
        Class<Object> pluginClass = pluginRegistry.getPluginClass(pluginClassName);
        return pluginClass != null && pluginRegistry.hasPlugin(pluginClass, classLoader, false);
    }

    /**
     * Initialize the singleton plugin manager.
     * <ul>
     * <li>Create new resource watcher using WatcherFactory and start it in separate thread.</li>
     * <li>Create new scheduler and start it in separate thread.</li>
     * <li>Scan for plugins</li>
     * <li>Register HotswapTransformer with the javaagent instrumentation class</li>
     * </ul>
     *
     * @param instrumentation javaagent instrumentation.
     */
    public void init(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;

        // create default configuration from this classloader
        ClassLoader classLoader = getClass().getClassLoader();
        classLoaderConfigurations.put(classLoader, new PluginConfiguration(classLoader));

        if (watcher == null) {
            try {
                watcher = new WatcherFactory().getWatcher();
            } catch (IOException e) {
                LOGGER.debug("Unable to create default watcher.", e);
            }
        }
        watcher.run();

        if (scheduler == null) {
            scheduler = new SchedulerImpl();
        }
        scheduler.run();

        pluginRegistry.scanPlugins(getClass().getClassLoader(), PLUGIN_PACKAGE);

        LOGGER.debug("Registering transformer ");
        instrumentation.addTransformer(hotswapTransformer);
    }

    ClassLoaderDefineClassPatcher classLoaderPatcher = new ClassLoaderDefineClassPatcher();
    Map<ClassLoader, PluginConfiguration> classLoaderConfigurations = new HashMap<>();
    Set<ClassLoaderInitListener> classLoaderInitListeners = new HashSet<>();

    public void registerClassLoaderInitListener(ClassLoaderInitListener classLoaderInitListener) {
        classLoaderInitListeners.add(classLoaderInitListener);

        // call init on this classloader immediately, because it is already initialized
        classLoaderInitListener.onInit(getClass().getClassLoader());
    }

    public void initClassLoader(ClassLoader classLoader) {
        // use default protection domain
        initClassLoader(classLoader, classLoader.getClass().getProtectionDomain());
    }

    public void initClassLoader(ClassLoader classLoader, ProtectionDomain protectionDomain) {

        if (classLoaderConfigurations.containsKey(classLoader))
            return;

        // parent of current classloader (system/bootstrap)
        if (getClass().getClassLoader() != null &&
            classLoader != null &&
            classLoader.equals(getClass().getClassLoader().getParent()))
            return;

        // synchronize ClassLoader patching - multiple classloaders may be patched at the same time
        // and they may synchronize loading for security reasons and introduce deadlocks
        synchronized (this) {
            if (classLoaderConfigurations.containsKey(classLoader))
                return;

            // transformation
            if (classLoader != null && classLoaderPatcher.isPatchAvailable(classLoader)) {
                classLoaderPatcher.patch(getClass().getClassLoader(), PLUGIN_PACKAGE.replace(".", "/"),
                        classLoader, protectionDomain);
            }

            // create new configuration for the classloader
            PluginConfiguration pluginConfiguration = new PluginConfiguration(getPluginConfiguration(getClass().getClassLoader()), classLoader, false);
            classLoaderConfigurations.put(classLoader, pluginConfiguration);
            pluginConfiguration.init();
        }

        // call listeners
        for (ClassLoaderInitListener classLoaderInitListener : classLoaderInitListeners)
            classLoaderInitListener.onInit(classLoader);
    }

    /**
     * Remove any classloader reference and close all plugin instances associated with classloader.
     * This method is called typically after webapp undeploy.
     *
     * @param classLoader the classloader to cleanup
     */
    public void closeClassLoader(ClassLoader classLoader) {
        pluginRegistry.closeClassLoader(classLoader);
        classLoaderConfigurations.remove(classLoader);
        hotswapTransformer.closeClassLoader(classLoader);
    }


    public PluginConfiguration getPluginConfiguration(ClassLoader classLoader) {
        // if needed, iterate to first parent loader with a known configuration
        ClassLoader loader = classLoader;
        while (loader != null && !classLoaderConfigurations.containsKey(loader))
            loader = loader.getParent();

        return classLoaderConfigurations.get(loader);
    }

    //////////////////////////   AGENT SERVICES /////////////////////////////////////

    private PluginRegistry pluginRegistry;

    /**
     * Returns the plugin registry service.
     */
    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    /**
     * Sets the plugin registry service.
     */
    public void setPluginRegistry(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    protected HotswapTransformer hotswapTransformer;

    /**
     * Returns the hotswap transformer service.
     */
    public HotswapTransformer getHotswapTransformer() {
        return hotswapTransformer;
    }

    protected Watcher watcher;

    /**
     * Returns the watcher service.
     */
    public Watcher getWatcher() {
        return watcher;
    }

    protected Scheduler scheduler;

    /**
     * Returns the scheduler service.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Redefine the supplied set of classes using the supplied bytecode.
     *
     * This method operates on a set in order to allow interdependent changes to more than one class at the same time
     * (a redefinition of class A can require a redefinition of class B).
     *
     * @param reloadMap class -> new bytecode
     * @see java.lang.instrument.Instrumentation#redefineClasses(java.lang.instrument.ClassDefinition...)
     */
    public void hotswap(Map<Class<?>, byte[]> reloadMap) {
        if (instrumentation == null) {
            throw new IllegalStateException("Plugin manager is not correctly initialized - no instrumentation available.");
        }

        synchronized (reloadMap) {
            ClassDefinition[] definitions = new ClassDefinition[reloadMap.size()];
            String[] classNames = new String[reloadMap.size()];
            int i = 0;
            for (Map.Entry<Class<?>, byte[]> entry : reloadMap.entrySet()) {
                classNames[i] = entry.getKey().getName();
                definitions[i++] = new ClassDefinition(entry.getKey(), entry.getValue());
            }
            try {
                LOGGER.reload("Reloading classes {} (autoHotswap)", Arrays.toString(classNames));
                synchronized (hotswapLock) {
                    instrumentation.redefineClasses(definitions);
                }
                LOGGER.debug("... reloaded classes {} (autoHotswap)", Arrays.toString(classNames));
            } catch (Exception e) {
                LOGGER.debug("... Fail to reload classes {} (autoHotswap), msg is {}", Arrays.toString(classNames), e);
                throw new IllegalStateException("Unable to redefine classes", e);
            }
            reloadMap.clear();
        }
    }

    /**
     * @return the instrumentation
     */
    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Redefine the supplied set of classes using the supplied bytecode in scheduled command. Actual hotswap is postponed by timeout
     *
     * This method operates on a set in order to allow interdependent changes to more than one class at the same time
     * (a redefinition of class A can require a redefinition of class B).
     *
     * @param reloadMap class -> new bytecode
     * @see java.lang.instrument.Instrumentation#redefineClasses(java.lang.instrument.ClassDefinition...)
     */
    public void scheduleHotswap(Map<Class<?>, byte[]> reloadMap, int timeout) {
        if (instrumentation == null) {
            throw new IllegalStateException("Plugin manager is not correctly initialized - no instrumentation available.");
        }
        getScheduler().scheduleCommand(new ScheduledHotswapCommand(reloadMap), timeout);
    }

}
