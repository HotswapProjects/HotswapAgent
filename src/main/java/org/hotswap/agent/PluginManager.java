package org.hotswap.agent;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.impl.SchedulerImpl;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.classloader.ClassLoaderDefineClassPatcher;
import org.hotswap.agent.util.classloader.ClassLoaderPatcher;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.WatcherFactory;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

/**
 * Main manager, central well known singleton controller.
 *
 * @author Jiri Bubnik
 */
public class PluginManager {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginManager.class);

    // singleton instance
    private static PluginManager INSTANCE = new PluginManager();

    private PluginRegistry pluginRegistry;

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public void setPluginRegistry(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Get singleton instance of the plugin manager.
     */
    public static PluginManager getInstance() {
        return INSTANCE;
    }

    // ensure singleton
    private PluginManager() {
        hotswapTransformer = new HotswapTransformer();
        pluginRegistry = new PluginRegistry(this);

        // create default configuration from this classloader
        ClassLoader classLoader = getClass().getClassLoader();
        classLoaderConfigurations.put(classLoader, new PluginConfiguration(classLoader));
    }


    public Object getPlugin(String clazz, ClassLoader classLoader) {
        try {
            return getPlugin(Class.forName(clazz), classLoader);
        } catch (ClassNotFoundException e) {
            throw new Error("Plugin class not found " + clazz, e);
        }
    }

    /**
     * Returns plugin instance by it's type and classLoader.
     *
     * @param clazz       type of the plugin
     * @param classLoader classloader of the plugin
     * @param <T>         type of the plugin to return correct instance.
     * @return the plugin or null if not found.
     */
    public <T> T getPlugin(Class<T> clazz, ClassLoader classLoader) {
        return pluginRegistry.getPlugin(clazz, classLoader);
    }

    protected HotswapTransformer hotswapTransformer;

    public HotswapTransformer getHotswapTransformer() {
        return hotswapTransformer;
    }

    protected Watcher watcher;

    public Watcher getWatcher() {
        return watcher;
    }

    protected Scheduler scheduler;

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

        pluginRegistry.scanPlugins();

        LOGGER.debug("Registering transformer ");
        instrumentation.addTransformer(hotswapTransformer);
    }

    ClassLoaderPatcher classLoaderPatcher = new ClassLoaderDefineClassPatcher();
    Map<ClassLoader, PluginConfiguration> classLoaderConfigurations = new HashMap<ClassLoader, PluginConfiguration>();

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
            classLoaderPatcher.patch(classLoader, protectionDomain);
        }

        // create new configuration for the classloader
        PluginConfiguration configuration = new PluginConfiguration(getPluginConfiguration(getClass().getClassLoader()), classLoader);
        classLoaderConfigurations.put(classLoader, configuration);
    }

    public void registerResourceListener(URI uri, WatchEventListener watchEventListener) throws IOException {
        watcher.addDirectory(uri);
        watcher.addEventListener(uri, watchEventListener);
    }

    public void scheduleCommand(Command command) {
        scheduler.scheduleCommand(command);
    }

    public PluginConfiguration getPluginConfiguration(ClassLoader classLoader) {
        // if needed, iterate to first parent loader with a known configuration
        ClassLoader loader = classLoader;
        while (loader != null && !classLoaderConfigurations.containsKey(loader))
            loader = loader.getParent();

        return classLoaderConfigurations.get(loader);
    }
}
