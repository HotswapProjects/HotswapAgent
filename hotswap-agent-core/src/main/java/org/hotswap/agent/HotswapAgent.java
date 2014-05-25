package org.hotswap.agent;


import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.Version;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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

    public static void premain(String args, Instrumentation inst) {

        LOGGER.info("Loading Hotswap agent {{}} - unlimited runtime class redefinition.", Version.version());
        PluginManager.getInstance().init(inst);
        LOGGER.debug("Hotswap agent inicialized.");

    }


}
