package org.hotswap.agent.plugin.owb.beans;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;

import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.context.creational.CreationalContextImpl;
import org.hotswap.agent.logging.AgentLogger;

/**
 * The Class ContextualReloadHelper.
 *
 * @author alpapad@gmail.com
 */
public class ContextualReloadHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ContextualReloadHelper.class);

    public static void reload(OwbHotswapContext ctx) {
        Set<Contextual<Object>> beans = ctx.$$ha$getBeansToReloadOwb();

        if (beans != null && !beans.isEmpty()) {
            LOGGER.debug("Starting re-loading {} beans in context '{}'", beans.size(), ctx);

            Iterator<Contextual<Object>> it = beans.iterator();
            while (it.hasNext()) {
                Contextual<Object> managedBean = it.next();
                destroy(ctx, managedBean);
            }
            beans.clear();
            LOGGER.debug("Finished re-loading beans in context '{}'", ctx);
        }
    }

    /**
     * Tries to add the bean in the context so it is reloaded in the next activation of the context.
     *
     * @param ctx
     * @param managedBean
     * @return
     */
    @SuppressWarnings("unchecked")
    public static boolean addToReloadSet(Context ctx,  Contextual<?> managedBean)  {
        try {
            LOGGER.debug("Adding bean '{}' to context '{}'", managedBean, ctx.getClass());
            Field toRedefine = ctx.getClass().getField("$$ha$toReloadOwb");
            Set toReload = Set.class.cast(toRedefine.get(ctx));
            if (toReload == null) {
                toReload = new HashSet();
                toRedefine.set(ctx, toReload);
            }
            toReload.add(managedBean);
            return true;
        } catch(Exception e) {
            LOGGER.warning("Context '{}' is not patched. Can not add bean '{}' to reload set", e, ctx, managedBean);
        }
        return false;
    }

    /**
     * Will remove bean from context forcing a clean new instance to be created (eg calling post-construct)
     *
     * @param ctx
     * @param managedBean
     */
    static void destroy(OwbHotswapContext ctx, Contextual<?> managedBean ) {
        try {
            LOGGER.debug("Removing bean '{}' from context '{}'", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                ctx.destroy(managedBean);
            }
            get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.error("Error removing ManagedBean '{}', it still exists as instance '{}'", managedBean, get);
                ctx.destroy(managedBean);
            }
        } catch (Exception e) {
            LOGGER.error("Error destoying bean '{}' in context '{}'", e, managedBean, ctx);
        }
    }

    /**
     * Will re-inject any managed beans in the target. Will not call any other life-cycle methods
     *
     * @param ctx
     * @param managedBean
     */
    @SuppressWarnings("unchecked")
    static void reinitialize(Context ctx, Contextual<Object> contextual) {
        try {
            ManagedBean<Object> managedBean = ManagedBean.class.cast(contextual);
            LOGGER.debug("Re-Initializing bean '{}' in context '{}'", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.debug("Bean injection points are reinitialized '{}'", managedBean);
                CreationalContextImpl<Object> creationalContext = managedBean.getWebBeansContext().getCreationalContextFactory().getCreationalContext(managedBean);
                managedBean.getProducer().inject(get, creationalContext);
            }
        } catch (Exception e) {
            LOGGER.error("Error reinitializing bean '{}' in context '{}'", e, contextual, ctx);
        }
    }
}
