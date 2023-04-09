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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.deltaspike.core.api.provider.BeanManagerProvider;
import org.apache.deltaspike.partialbean.impl.PartialBeanProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.DeltaspikeClassSignatureHelper;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class PartialBeanClassRefreshAgent.
 *
 * @author Vladimir Dvorak
 */
public class PartialBeanClassRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PartialBeanClassRefreshAgent.class);

    public static void refreshPartialBeanClass(ClassLoader classLoader, Object partialBean, String oldSignaturesForProxyCheck) {
        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        Bean<?> bean = (Bean<?>) partialBean;
        Class<?> beanClass = bean.getBeanClass();

        String newClassSignature = DeltaspikeClassSignatureHelper.getSignaturePartialBeanClass(beanClass);
        if (newClassSignature != null && newClassSignature.equals(oldSignaturesForProxyCheck)) {
            return;
        }

        ProxyClassLoadingDelegate.beginProxyRegeneration();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            Object lifecycle = (Object) ReflectionHelper.get(partialBean, "lifecycle");
            if (lifecycle != null) {
                Class<?> targetClass = (Class) ReflectionHelper.get(lifecycle, "targetClass");
                PartialBeanProxyFactory proxyFactory = PartialBeanProxyFactory.getInstance();
                try {
                    // Deltaspike 1.5
                    Method m3 = PartialBeanProxyFactory.class.getMethod("getProxyClass", new Class[] { BeanManager.class, Class.class, Class.class} );
                    Class<? extends InvocationHandler> delegateInvocationHandlerClass = (Class) ReflectionHelper.get(lifecycle, "delegateInvocationHandlerClass");
                    m3.invoke(proxyFactory, new Object[] {BeanManagerProvider.getInstance().getBeanManager(), targetClass, delegateInvocationHandlerClass} );
                } catch (NoSuchMethodException e) {
                    // Deltaspike 1.7
                    Method m2 = PartialBeanProxyFactory.class.getMethod("getProxyClass", new Class[] { BeanManager.class, Class.class } );
                    m2.invoke(proxyFactory, new Object[] {BeanManagerProvider.getInstance().getBeanManager(), targetClass} );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Deltaspike proxy redefinition failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }

    }
}
