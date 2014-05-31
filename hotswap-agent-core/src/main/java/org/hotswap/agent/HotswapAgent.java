package org.hotswap.agent;


import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.Version;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Register the agent and initialize plugin manager singleton instance.
 * <p/>
 * This class must be registered in META-INF/MANIFEST.MF:
 * Premain-Class: org.hotswap.agent.HotswapAgent
 * <p/>
 * Use with -javaagent agent.jar to use with an application.
 *
 * @author Jiri Bubnik
 */
public class HotswapAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapAgent.class);

    /**
     * Force disable plugin, this plugin is skipped during scanning process.
     *
     * Plugin might be disabled in hotswap-agent.properties for application classloaders as well.
     */
    private static Set<String> disabledPlugins = new HashSet<String>();

    /**
     * Default value for autoHotswap property.
     */
    private static boolean autoHotswap = false;

    public static void premain(String args, Instrumentation inst) {

        LOGGER.info("Loading Hotswap agent {{}} - unlimited runtime class redefinition.", Version.version());
        parseArgs(args);
        PluginManager.getInstance().init(inst);
        LOGGER.debug("Hotswap agent inicialized.");

    }

    public static void parseArgs(String args) {
        if (args == null)
            return;

        for (String arg : args.split(",")) {
            String[] val = arg.split("=");
            if (val.length != 2) {
                LOGGER.warning("Invalid javaagent command line argument '{}'. Argument is ignored.", arg);
            }

            String option = val[0];
            String optionValue = val[1];
            if ("disablePlugin".equals(option)) {
                disabledPlugins.add(optionValue);
            } else if ("autoHotswap".equals(option)) {
                autoHotswap = Boolean.valueOf(optionValue);
            }else {
                LOGGER.warning("Invalid javaagent option '{}'. Argument '{}' is ignored.", option, arg);
            }
        }
    }


    /**
     * Checks if the plugin is disabled (by name).
     * @param pluginName plugin name (e.g. Tomcat, Spring, ...)
     * @return true if the plugin is disabled
     */
    public static boolean isPluginDisabled(String pluginName) {
        return disabledPlugins.contains(pluginName);
    }

    /**
     * Default autoHotswap property value.
     * @return true if autoHotswap=true command line option was specified
     */
    public static boolean isAutoHotswap() {
        return autoHotswap;
    }
}
