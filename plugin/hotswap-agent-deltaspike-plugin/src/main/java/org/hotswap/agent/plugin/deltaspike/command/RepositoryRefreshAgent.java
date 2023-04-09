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
package org.hotswap.agent.plugin.deltaspike.command;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.deltaspike.data.impl.meta.RepositoryMetadataHandler;
import org.apache.deltaspike.partialbean.impl.PartialBeanProxyFactory;
import org.apache.deltaspike.proxy.spi.DeltaSpikeProxy;
import org.apache.deltaspike.proxy.spi.invocation.DeltaSpikeProxyInterceptorLookup;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.transformer.RepositoryTransformer;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Handle redefinition of deltaspike repository
 *
 * @author Vladimir Dvorak
 */
public class RepositoryRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepositoryRefreshAgent.class);

    public static boolean reloadFlag = false;

    /**
     * Reload bean in existing bean manager. Called by a reflection command from BeanRefreshCommand transformer.
     *
     * @param appClassLoader the application class loader
     * @param repoClassName the repo class name
     */
    public static void refreshHandler(ClassLoader appClassLoader, String repoClassName, List repositoryProxies) {

        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(appClassLoader);
            Class<?> repoClass = appClassLoader.loadClass(repoClassName);

            RepositoryMetadataHandler handler = getInstance(RepositoryMetadataHandler.class);
            if (handler != null) {
                ReflectionHelper.invoke(handler, handler.getClass(), RepositoryTransformer.REINITIALIZE_METHOD, new Class[] { Class.class }, repoClass);
            } else {
                LOGGER.debug("{} bean not found.", RepositoryMetadataHandler.class.getName());
            }

            Method[] delegateMethods = PartialBeanProxyFactory.getInstance().getDelegateMethods(repoClass);
            for (Object proxyObject: repositoryProxies) {
                if (proxyObject instanceof DeltaSpikeProxy) {
                    ((DeltaSpikeProxy) proxyObject).setDelegateMethods(delegateMethods);
                }
            }
            DeltaSpikeProxyInterceptorLookup lookup = getInstance(DeltaSpikeProxyInterceptorLookup.class);
            if (lookup != null) {
                Map cache  = (Map) ReflectionHelper.get(lookup, "cache");
                if (cache != null) {
                    cache.clear();
                }
            }
            LOGGER.info("Deltaspike repository {} refreshed.", repoClassName);
            RepositoryRefreshAgent.reloadFlag = true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("Repository class '{}' not found.", repoClassName, e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    public static <T> T getInstance(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = resolve(beanManager, beanClass);
        return getInstance(beanManager, bean);
    }

    private static <T> Bean<T> resolve(BeanManager beanManager, Class<T> beanClass) {
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);

        for (Bean<?> bean : beans) {
            if (bean.getBeanClass() == beanClass) {
                return (Bean<T>) beanManager.resolve(Collections.<Bean<?>> singleton(bean));
            }
        }
        return (Bean<T>) beanManager.resolve(beans);
    }

    private static <T> T getInstance(BeanManager beanManager, Bean<T> bean) {
        Context context = beanManager.getContext(bean.getScope());
        return context.get(bean);
    }
}
