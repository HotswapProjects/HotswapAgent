package org.hotswap.agent.plugin.weld.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.weld.context.beanstore.BoundBeanStore;
import org.jboss.weld.context.beanstore.SessionMapBeanStore;

/**
 * The Class BoundSessionBeanStoreRegistry.
 */
public class BoundSessionBeanStoreRegistry {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BoundSessionBeanStoreRegistry.class);

    private Set<Map<String, Object>> beanStores =
            Collections.newSetFromMap(new java.util.WeakHashMap<Map<String, Object>, Boolean>());

    public void addBeanStore(Map<String, Object> benStore) {
        if (benStore != null) {
            beanStores.add(benStore);
        }
    }

    @SuppressWarnings("unchecked")
    public void removeBeanStore(BoundBeanStore beanStore) {
        if (beanStore != null) {
            if (beanStore instanceof SessionMapBeanStore) {
                Map<String, Object> delegate = (Map<String, Object>) ReflectionHelper.get(beanStore, "delegate");
                beanStores.remove(delegate);
            } else {
               LOGGER.error("Bean store removal failed. SessionMapBeanStore expected.");
            }
        }
    }

    public List<Map<String, Object>> getBeanStores() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(beanStores);
        return result;
    }

}

