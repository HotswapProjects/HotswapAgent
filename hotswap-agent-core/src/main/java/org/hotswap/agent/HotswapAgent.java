/*
 * Copyright 2013-2019 the HotswapAgent authors.
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
package org.hotswap.agent;

import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.util.Version;
import org.hotswap.agent.config.LogConfigurationHelper;

/**
 * Register the agent and initialize plugin manager singleton instance.
 * <p/>
 * This class must be registered in META-INF/MANIFEST.MF:
 * Agent-Class: org.hotswap.agent.HotswapAgent
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
     * <p/>
     * Plugin might be disabled in hotswap-agent.properties for application classloaders as well.
     */
      private static Set<String> disabledPlugins = new HashSet<>();

    /**
     * Default value for autoHotswap property.
     */
    private static boolean autoHotswap = false;

    /**
     * Path for an external properties file `hotswap-agent.properties`
     */
    private static String propertiesFilePath;

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    public static void premain(String args, Instrumentation inst) {

        parseArgs(args);
        LOGGER.info("Loading Hotswap agent {{}} - unlimited runtime class redefinition.", Version.version());
        fixJboss7Modules();
        PluginManager.getInstance().init(inst);
        LOGGER.debug("Hotswap agent initialized.");

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
                disabledPlugins.add(optionValue.toLowerCase());
            } else if ("autoHotswap".equals(option)) {
                autoHotswap = Boolean.valueOf(optionValue);
            } else if ("propertiesFilePath".equals(option)) {
                propertiesFilePath = optionValue;
            } else if ("LOGGER".equals(option)) {
            	AgentLogger.Level level = LogConfigurationHelper.getLevel(option, optionValue);
            	AgentLogger.setLevel(level);
            } else {
                LOGGER.warning("Invalid javaagent option '{}'. Argument '{}' is ignored.", option, arg);
            }
        }
    }

    /**
     * @return the path for the hotswap-agent.properties external file
     */
    public static String getExternalPropertiesFile() {
        return propertiesFilePath;
    }


    /**
     * Checks if the plugin is disabled (by name).
     *
     * @param pluginName plugin name (e.g. Tomcat, Spring, ...)
     * @return true if the plugin is disabled
     */
    public static boolean isPluginDisabled(String pluginName) {
        return disabledPlugins.contains(pluginName.toLowerCase());
    }

    /**
     * Default autoHotswap property value.
     *
     * @return true if autoHotswap=true command line option was specified
     */
    public static boolean isAutoHotswap() {
        return autoHotswap;
    }

    /**
     * JBoss 7 use OSGI classloading and hence agent core classes are not available from application classloader
     * (this is not the case with standard classloaders with parent delgation).
     *
     * Wee need to simulate command line attribute -Djboss.modules.system.pkgs=org.hotswap.agent to allow any
     * classloader to access agent libraries (OSGI default export). This method does it on behalf of the user.
     *
     * It is not possible to add whole org.hotswap.agent package, because it includes all subpackages and
     * examples will fail (org.hotswap.agent.example will become system package).
     *
     * See similar problem description https://issues.jboss.org/browse/WFLY-895.
     */
    private static void fixJboss7Modules() {
        String JBOSS_SYSTEM_MODULES_KEY = "jboss.modules.system.pkgs";


        String oldValue = System.getProperty(JBOSS_SYSTEM_MODULES_KEY, null);
        System.setProperty(JBOSS_SYSTEM_MODULES_KEY, oldValue == null ? HOTSWAP_AGENT_EXPORT_PACKAGES : oldValue + "," + HOTSWAP_AGENT_EXPORT_PACKAGES);
    }

    public static final String HOTSWAP_AGENT_EXPORT_PACKAGES = //
            "org.hotswap.agent.annotation,"//
            + "org.hotswap.agent.command," //
            + "org.hotswap.agent.config," //
            + "org.hotswap.agent.logging,"
            + "org.hotswap.agent.plugin," //
            + "org.hotswap.agent.util," //
            + "org.hotswap.agent.watch," //
            + "org.hotswap.agent.versions," //
            + "org.hotswap.agent.javassist";
}
