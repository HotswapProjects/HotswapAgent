package org.hotswap.agent;


import org.hotswap.agent.logging.AgentLogger;

import java.lang.instrument.Instrumentation;

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

        LOGGER.info("Loading Hotswap agent - unlimited runtime class redefinition.");
        PluginManager.getInstance().init(inst);
        LOGGER.debug("Hotswap agent inicialized.");

    }

}
