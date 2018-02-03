package org.hotswap.agent.plugin.owb.command;

import java.util.Map;

import org.apache.webbeans.context.creational.BeanInstanceBag;
import org.hotswap.agent.plugin.owb.transformer.CdiContextsTransformer;
import org.hotswap.agent.util.ReflectionHelper;

public class CustomContextTrackersAttacher {

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
                                   CdiContextsTransformer.ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD,
                                   new Class[] {java.lang.Object.class},
                                   context);
                       } catch (Exception e) {
                       }
                    }
                }
            }
        }
    }

}
