package org.hotswap.agent.plugin.hotswapper;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hotswap class changes directly via JPDA API.
 * <p/>
 * This plugin creates an instance for each classloader with autoHotswap agent property set. Then it listens
 * for .class file change and executes hotswap via JPDA API.
 *
 * @author Jiri Bubnik
 * @see org.hotswap.agent.plugin.hotswapper.HotSwapper
 */
@Plugin(name = "HotswapperPlugin", description = "Listen to class change and reload (hotswap) it on the fly via " +
        "Java Platform Debugger Architecture (JPDA) directly. " +
        "Although it is usually more convenient to use your IDE debugger for hotswap during development, this " +
        "can be utilized to reload classes even on production server! Be careful and test it thoroughly at first :-)",
        testedVersions = {"JDK 1.7.0_45"}, expectedVersions = {"JDK 1.5+"})
public class HotswapperPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapperPlugin.class);

    @Init
    Scheduler scheduler;

    // synchronize on this map to wait for previous processing
    final Map<String, byte[]> reloadMap = new HashMap<String, byte[]>();

    Command hotswapCommand;

    /**
     * For each changed class create a reload command.
     */
    @Watch(path = ".", filter = ".*.class", watchEvents = {WatchEvent.WatchEventType.CREATE, WatchEvent.WatchEventType.MODIFY})
    public void watchReload(CtClass ctClass) throws IOException, CannotCompileException {
        LOGGER.debug("Reload class {}", ctClass.getName());
        synchronized (reloadMap) {
            reloadMap.put(ctClass.getName(), ctClass.toBytecode());
            scheduler.scheduleCommand(hotswapCommand);
        }
    }

    /**
     * Create a hotswap command using hotSwappper.
     *
     * @param appClassLoader it can be run in any classloader with tools.jar on classpath. AppClassLoader can
     *                       be setup by maven dependency (jetty plugin), use this classloader.
     * @param port           attach the hotswapper
     */
    public void initHotswapCommand(ClassLoader appClassLoader, String port) {
        hotswapCommand = new ReflectionCommand(this, HotswapperCommand.class.getName(), "hotswap", appClassLoader,
                port, reloadMap);
    }

    /**
     * For each classloader check for autoHotswap configuration instance with hotswapper.
     */
    @Init
    public static void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
        LOGGER.debug("Init plugin at classLoader {}", appClassLoader);

        // init only if the classloader contains directly the property file (not in parent classloader)
        if (!pluginConfiguration.containsPropertyFile()) {
            LOGGER.debug("ClassLoader {} does not contain hotswap-agent.properties file, hotswapper skipped.", appClassLoader);
            return;
        }

        // and autoHotswap enabled
        if (!pluginConfiguration.getPropertyBoolean("autoHotswap")) {
            LOGGER.debug("ClassLoader {} does hotswap-agent.properties autoHotswap=false, hotswapper skipped.", appClassLoader);
            return;
        }


        String port = pluginConfiguration.getProperty("autoHotswap.port");

        HotswapperPlugin plugin = PluginManagerInvoker.callInitializePlugin(HotswapperPlugin.class, appClassLoader);
        plugin.initHotswapCommand(appClassLoader, port);
    }
}
