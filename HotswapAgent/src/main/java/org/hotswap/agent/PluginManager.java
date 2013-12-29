package org.hotswap.agent;

import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.impl.SchedulerImpl;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.classloader.ClassLoaderDefineClassPatcher;
import org.hotswap.agent.util.classloader.ClassLoaderPatcher;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.WatcherFactory;
import sun.management.resources.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

        // create default configuration from this classloader
        ClassLoader classLoader = getClass().getClassLoader();
        classLoaderConfigurations.put(classLoader, new PluginConfiguration(classLoader));
    }

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

    ClassLoaderPatcher classLoaderPatcher = new ClassLoaderDefineClassPatcher();
    Map<ClassLoader, PluginConfiguration> classLoaderConfigurations = new HashMap<ClassLoader, PluginConfiguration>();
    Set<ClassLoaderInitListener> classLoaderInitListeners = new HashSet<ClassLoaderInitListener>();

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
        // parent of current classloader (system/bootstrap)
        if (classLoader.equals(getClass().getClassLoader().getParent()))
            return;

        if (classLoaderConfigurations.containsKey(classLoader))
            return;


        // transformation
        if (classLoaderPatcher.isPatchAvailable(classLoader)) {
            classLoaderPatcher.patch(getClass().getClassLoader(), PLUGIN_PACKAGE.replace(".", "/"),
                    classLoader, protectionDomain);
        }

        // create new configuration for the classloader
        PluginConfiguration configuration = new PluginConfiguration(getPluginConfiguration(getClass().getClassLoader()), classLoader);
        classLoaderConfigurations.put(classLoader, configuration);

        // call listeners
        for (ClassLoaderInitListener classLoaderInitListener : classLoaderInitListeners)
            classLoaderInitListener.onInit(classLoader);
    }

    public void registerResourceListener(URI uri, WatchEventListener watchEventListener) throws IOException {
        watcher.addDirectory(uri);
        watcher.addEventListener(uri, watchEventListener);
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
}
