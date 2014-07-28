package org.hotswap.agent.plugin.tomcat;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;
import sun.reflect.Reflection;

import java.net.URL;
import java.util.Arrays;

/**
 * Created by bubnik on 4.6.2014.
 */
public class WatcherTomcatWebappClassLoader extends WatchResourcesClassLoader {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatcherTomcatWebappClassLoader.class);

    public WatcherTomcatWebappClassLoader(ClassLoader originalClassLoader) {
        super(originalClassLoader);

        PluginManagerInvoker.callInitializePlugin(TomcatPlugin.class, originalClassLoader);

        //PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader, "init"
        URL[] extraClassPath = (URL[]) PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader,
                "getExtraClassPath", new Class[] {}, new Object[] {});

        LOGGER.debug("extraClassPath = {}", extraClassPath);
        if (extraClassPath.length > 0) {
            LOGGER.debug("Registering extraClasspath {} to classloader {}", extraClassPath, originalClassLoader);
            initExtraPath(extraClassPath);
        }

        URL[] watchResources = (URL[]) PluginManagerInvoker.callPluginMethod(TomcatPlugin.class, originalClassLoader,
                "getWatchResources", new Class[] {}, new Object[] {});

        System.err.println("watchResources =  " + Arrays.toString(watchResources));
        LOGGER.debug("watchResources = {}", watchResources);
        if (watchResources.length > 0) {
            LOGGER.debug("Registering watchResources {} to classloader {}", extraClassPath, originalClassLoader);
            initWatchResources(watchResources, PluginManager.getInstance().getWatcher());
        }
    }
}
