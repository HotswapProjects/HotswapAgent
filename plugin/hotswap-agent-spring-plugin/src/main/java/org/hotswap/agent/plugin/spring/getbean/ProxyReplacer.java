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
package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;

/**
 * Proxies the beans. The beans inside these proxies can be cleared.
 *
 * @author Erki Ehtla
 *
 */
public class ProxyReplacer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyReplacer.class);
    private static Class<?> infrastructureProxyClass;
    /**
     * Name of the Spring beanFactory method, which returns a bean
     */
    public static final String FACTORY_METHOD_NAME = "getBean";

    /**
     * Clears the bean references inside all the proxies
     */
    public static void clearAllProxies() {
        DetachableBeanHolder.detachBeans();
    }

    /**
     * Creates a proxied Spring bean. Called from within WebApp code by modification of Spring classes
     *
     * @param beanFactry
     *            Spring beanFactory
     * @param bean
     *            Spring bean
     * @param paramClasses
     *            Parameter Classes of the Spring beanFactory method which returned the bean. The method is named
     *            ProxyReplacer.FACTORY_METHOD_NAME
     * @param paramValues
     *            Parameter values of the Spring beanFactory method which returned the bean. The method is named
     *            ProxyReplacer.FACTORY_METHOD_NAME
     * @return Proxied bean
     */
    public static Object register(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
        if (bean == null) {
            return bean;
        }
        if (SpringPlugin.basePackagePrefixes != null) {
            boolean hasMatch = false;
            for (String basePackagePrefix : SpringPlugin.basePackagePrefixes) {
                if (bean.getClass().getName().startsWith(basePackagePrefix)) {
                    hasMatch = true;
                    break;
                }
            }

            // bean from other package
            if (!hasMatch) {
                LOGGER.info("{} not in basePackagePrefix", bean.getClass().getName());
                return bean;
            }
        }

        // create proxy for prototype-scope beans and apsect proxied beans
        if (bean.getClass().getName().startsWith("com.sun.proxy.$Proxy")) {
            InvocationHandler handler = new HotswapSpringInvocationHandler(bean, beanFactry, paramClasses, paramValues);
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            try {
                if (!Arrays.asList(interfaces).contains(getInfrastructureProxyClass())) {
                    interfaces = Arrays.copyOf(interfaces, interfaces.length + 1);
                    interfaces[interfaces.length - 1] = getInfrastructureProxyClass();
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error("error adding org.springframework.core.InfrastructureProxy to proxy class", e);
            }
            // fix: it should be the classloader of the bean,
            // or org.springframework.beans.factory.support.AbstractBeanFactory.getBeanClassLoader,
            // but not the classLoader of the beanFactry
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces, handler);
        } else if (EnhancerProxyCreater.isSupportedCglibProxy(bean)) {
            // already a proxy, skip..
            if (bean.getClass().getName().contains("$HOTSWAPAGENT_")) {
                return bean;
            }

            return EnhancerProxyCreater.createProxy(beanFactry, bean, paramClasses, paramValues);
        }

        return bean;
    }

    private static Class<?> getInfrastructureProxyClass() throws ClassNotFoundException {
        if (infrastructureProxyClass == null) {
            infrastructureProxyClass = ProxyReplacer.class.getClassLoader().loadClass("org.springframework.core.InfrastructureProxy");
        }
        return infrastructureProxyClass;
    }
}