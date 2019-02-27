package org.hotswap.agent.plugin.cxf.jaxrs;

import java.lang.ref.WeakReference;
import java.util.List;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.cdi.HaCdiExtraContext;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class HaCdiExtraCxfContext.
 */
public class HaCdiExtraCxfContext implements HaCdiExtraContext {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HaCdiExtraCxfContext.class);

    public static void registerExtraContext(Object pluginInstance) {
        HaCdiExtraCxfContext context = new HaCdiExtraCxfContext(pluginInstance);
        HaCdiCommons.registerExtraContext(context);
    }

    WeakReference<Object> pluginReference;

    HaCdiExtraCxfContext(Object plugin) {
        pluginReference = new WeakReference<>(plugin);
    }

    public boolean containsBeanInstances(Class<?> beanClass) {
        Object plugin = pluginReference.get();
        if (plugin != null) {
            try {
                return (boolean) ReflectionHelper.invoke(plugin, plugin.getClass(), "containsServiceInstance",
                        new Class[] { Class.class }, beanClass);
            } catch (Exception e) {
                LOGGER.error("containsBeanInstances() exception {}", e);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getBeanInstances(Class<?> beanClass) {
        Object plugin = pluginReference.get();
        if (plugin != null) {
            try {
                return (List<Object>) ReflectionHelper.invoke(plugin, plugin.getClass(), "getServiceInstances",
                        new Class[] { Class.class }, beanClass);
            } catch (Exception e) {
                LOGGER.error("getBeanInstances() exception {}", e);
            }
        }
        return null;
    }
}
