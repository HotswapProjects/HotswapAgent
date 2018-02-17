package org.hotswap.agent.plugin.owb.command;

import java.util.Map;

import org.apache.webbeans.context.creational.BeanInstanceBag;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Attach custom context trackers after bean de-passivation
 *
 * @author Vladimir Dvorak
 */
public class CustomContextTrackersAttacher {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CustomContextTrackersAttacher.class);

    /**
     * Attach custom context trackers to session.
     *
     * @param map the map
     */
    public static void attachTrackers(Object context, Map map) {
        if (map != null) {
            for (Object o: map.values()) {
                if (o instanceof BeanInstanceBag) {
                    Object instance = ((BeanInstanceBag ) o).getBeanInstance();
                    if (instance != null) {
                       try {
                           ReflectionHelper.invoke(instance, instance.getClass(),
                                   HaCdiCommons.ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD,
                                   new Class[] {java.lang.Object.class},
                                   context);
                       } catch (IllegalArgumentException e) {
                           // swallow
                       } catch (Exception e) {
                           LOGGER.error("Attach to custom context trackers failed.", e);
                       }
                    }
                }
            }
        }
    }

}
