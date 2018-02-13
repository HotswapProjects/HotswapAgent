package org.hotswap.agent.plugin.omnifaces;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Omnifaces (http://omnifaces.org/)
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Omnifaces",
        description = "Omnifaces (http://omnifaces.org//), support for view scope reinjection/reloading",
        testedVersions = {"2.6.8"},
        expectedVersions = {"2.6"},
        supportClass = { OmnifacesTransformer.class }
)
public class OmnifacesPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OmnifacesPlugin.class);

    private boolean initialized;

    public void init() {
        if (!initialized) {
            LOGGER.info("OmnifacesPlugin plugin initialized.");
        }
    }

}

