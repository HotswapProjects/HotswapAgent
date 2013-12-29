package org.hotswap.agent.plugin.hotswapper;

import org.hotswap.agent.logging.AgentLogger;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Hotswapper command must run in application classloader because tools.jar dependency can be easier added to
 * the application classloader than to java classpath.
 *
 * @author Jiri Bubnik
 */
public class HotswapperCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapperCommand.class);

    // HotSwapper will connect to JPDA on first hotswap command and remain connected.
    // The HotSwapper class from javaassist is copied to the plugin, becuse it needs to reside
    // in the application classloader to avoid NoClassDefFound error on tools.jar classes.
    private static HotSwapper hotSwapper = null;

    public void hotswap(String port, HashMap<String, byte[]> reloadMap) {
        synchronized (reloadMap) {
            if (hotSwapper == null) {
                LOGGER.debug("Starting HotSwapper agent on JPDA transport socket - port {}, classloader {}", port, getClass().getClassLoader());
                try {
                    hotSwapper = new HotSwapper(port);
                } catch (IOException e) {
                    LOGGER.error("Unable to connect to debug session. Did you start the application with debug enabled " +
                            "(i.e. java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000)", e);
                } catch (Exception e) {
                    LOGGER.error("Unable to connect to debug session. Please check port property setting '{}'.", e, port);
                }
            }

            if (hotSwapper != null) {
                LOGGER.debug("Reloading classes {}", Arrays.toString(reloadMap.keySet().toArray()));
                hotSwapper.reload(reloadMap);
                reloadMap.clear();
                LOGGER.debug("HotSwapper agent reload complete.");
            }
        }
    }
}
