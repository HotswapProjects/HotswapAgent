/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.weld.beans;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;

import org.hotswap.agent.logging.AgentLogger;
import org.jboss.weld.bean.ManagedBean;

/**
 * The Class ContextualReloadHelper.
 *
 * @author alpapad@gmail.com
 */
public class ContextualReloadHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ContextualReloadHelper.class);

    public static void reload(WeldHotswapContext ctx) {
        Set<Contextual<Object>> beans = ctx.$$ha$getBeansToReloadWeld();

        if (beans != null && !beans.isEmpty()) {
            LOGGER.debug("Starting re-loading Contextuals in {}, {}", ctx, beans.size());

            Iterator<Contextual<Object>> it = beans.iterator();
            while (it.hasNext()) {
                Contextual<Object> managedBean = it.next();
                destroy(ctx, managedBean);
            }
            beans.clear();
            LOGGER.debug("Finished re-loading Contextuals in {}", ctx);
        }
    }

    /**
     * Tries to add the bean in the context so it is reloaded in the next activation of the context.
     *
     * @param ctx
     * @param managedBean
     * @return
     */
    public static boolean addToReloadSet(Context ctx,  Contextual<Object> managedBean)  {
        try {
            LOGGER.debug("Adding bean in '{}' : {}", ctx.getClass(), managedBean);
            Field toRedefine = ctx.getClass().getDeclaredField("$$ha$toReloadWeld");
            Set toReload = Set.class.cast(toRedefine.get(ctx));
            if (toReload == null) {
                toReload = new HashSet();
                toRedefine.set(ctx, toReload);
            }
            toReload.add(managedBean);
            return true;
        } catch(Exception e) {
            LOGGER.warning("Context {} is not patched. Can not add {} to reload set", e, ctx, managedBean);
        }
        return false;
    }

    /**
     * Will remove bean from context forcing a clean new instance to be created (eg calling post-construct)
     *
     * @param ctx
     * @param managedBean
     */
    public static void destroy(WeldHotswapContext ctx, Contextual<?> managedBean ) {
        try {
            LOGGER.debug("Removing Contextual from Context........ {},: {}", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                ctx.destroy(managedBean);
            }
            get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.error("Error removing ManagedBean {}, it still exists as instance {} ", managedBean, get);
                ctx.destroy(managedBean);
            }
        } catch (Exception e) {
            LOGGER.error("Error destoying bean {},: {}", e, managedBean, ctx);
        }
    }

    /**
     * Will re-inject any managed beans in the target. Will not call any other life-cycle methods
     *
     * @param ctx
     * @param managedBean
     */
    public static void reinitialize(Context ctx, Contextual<Object> contextual) {
        try {
            ManagedBean<Object> managedBean = ManagedBean.class.cast(contextual);
            LOGGER.debug("Re-Initializing........ {},: {}", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.debug("Bean injection points are reinitialized '{}'", managedBean);
                managedBean.getProducer().inject(get, managedBean.getBeanManager().createCreationalContext(managedBean));
            }
        } catch (Exception e) {
            LOGGER.error("Error reinitializing bean {},: {}", e, contextual, ctx);
        }
    }
}
