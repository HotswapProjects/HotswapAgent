package org.hotswap.agent;


import org.hotswap.agent.logging.AgentLogger;

import java.lang.instrument.Instrumentation;

/**
 * Register this agent.
 *
 * @author Jiri Bubnik
 */
public class HotswapAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapAgent.class);

    public static void premain(String args, Instrumentation inst) {

        LOGGER.info("Starting Hotswap agent ...");
        PluginManager.getInstance().init(inst);
        LOGGER.info("Hotswap agent inicialized.");

    }

}
