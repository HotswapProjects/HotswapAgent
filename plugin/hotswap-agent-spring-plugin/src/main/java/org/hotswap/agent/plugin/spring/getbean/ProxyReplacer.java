/*
 * Copyright 2013-2026 the HotswapAgent authors.
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

import java.lang.reflect.Proxy;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxies the beans. The beans inside these proxies can be cleared.
 *
 * @author Erki Ehtla
 */
public class ProxyReplacer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyReplacer.class);
    /**
     * Name of the Spring beanFactory method, which returns a bean
     */
    public static final String FACTORY_METHOD_NAME = "getBean";

    /**
     * Marker interface implemented by @Configuration classes enhanced by Spring's ConfigurationClassEnhancer
     */
    private static final String ENHANCED_CONFIGURATION_CLASS_NAME =
            "org.springframework.context.annotation.ConfigurationClassEnhancer$EnhancedConfiguration";

    /**
     * Interface of Spring AOP proxies, which knows the class of their target
     */
    private static final String ADVISED_CLASS_NAME = "org.springframework.aop.framework.Advised";

    private static boolean isInBasePackage(String[] basePackagePrefixes, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            for (String basePackagePrefix : basePackagePrefixes) {
                if (clazz.getName().startsWith(basePackagePrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks the target class of a Spring AOP proxy. By name and reflection, Spring AOP may not be available.
     */
    private static boolean isTargetClassInBasePackage(String[] basePackagePrefixes, Object proxy) {
        for (Class<?> proxyInterface : proxy.getClass().getInterfaces()) {
            if (ADVISED_CLASS_NAME.equals(proxyInterface.getName())) {
                try {
                    // answered by the proxy itself, the target is not called
                    Object targetClass = proxyInterface.getMethod("getTargetClass").invoke(proxy);
                    return targetClass instanceof Class && isInBasePackage(basePackagePrefixes, (Class<?>) targetClass);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    LOGGER.debug("Unable to get the target class of the proxy {}", e, proxy.getClass().getName());
                }
            }
        }
        return false;
    }

    /**
     * Clears the bean references inside all the proxies
     */
    public static void clearAllProxies() {
        DetachableBeanHolder.detachBeans();
    }

    /**
     * Creates a proxied Spring bean. Called from within WebApp code by modification of Spring classes
     *
     * @param beanFactry   Spring beanFactory
     * @param bean         Spring bean
     * @param paramClasses Parameter Classes of the Spring beanFactory method which returned the bean. The method is named
     *                     ProxyReplacer.FACTORY_METHOD_NAME
     * @param paramValues  Parameter values of the Spring beanFactory method which returned the bean. The method is named
     *                     ProxyReplacer.FACTORY_METHOD_NAME
     * @return Proxied bean
     */
    public static Object register(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
        if (bean == null) {
            return bean;
        }

        String[] basePackagePrefixes;
        try {
            basePackagePrefixes = (String[]) PluginManager.getInstance().getPlugin("org.hotswap.agent.plugin.spring.SpringPlugin",
                    ProxyReplacer.class.getClassLoader()).getClass().getDeclaredField("basePackagePrefixes").get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("load org.hotswap.agent.plugin.spring.SpringPlugin failed, classLoader: {}, exception: {}",
                    ProxyReplacer.class.getClassLoader(), e);
            return bean;
        }

        // JDK proxies (e.g. Spring AOP proxies of beans with interfaces) are not wrapped, their invocation handler is
        // replaced after a reload. They are recognized by Proxy.isProxyClass(), as their class name is
        // com.sun.proxy.$ProxyN up to Java 15, but jdk.proxyN.$ProxyM since Java 16. basePackagePrefix is checked
        // against their interfaces, and against the target class of a Spring AOP proxy, which may implement only
        // interfaces of libraries.
        if (Proxy.isProxyClass(bean.getClass())) {
            if (Proxy.getInvocationHandler(bean) instanceof RefreshingInvocationHandler) {
                // Tracked already. It gets its bean with this method when it is called, also to tell its target class.
                return bean;
            }
            if (basePackagePrefixes == null || isInBasePackage(basePackagePrefixes, bean.getClass().getInterfaces())
                    || isTargetClassInBasePackage(basePackagePrefixes, bean)) {
                JdkProxyRefresher.track(beanFactry, bean, paramClasses, paramValues);
            }
            return bean;
        }

        if (basePackagePrefixes != null && !isInBasePackage(basePackagePrefixes, bean.getClass())) {
            // bean from other package
            LOGGER.info("{} not in basePackagePrefix", bean.getClass().getName());
            return bean;
        }

        if (EnhancerProxyCreater.isSupportedCglibProxy(bean)) {
            // already a proxy, skip..
            if (bean.getClass().getName().contains("$HOTSWAPAGENT_")) {
                return bean;
            }

            // @Configuration classes enhanced by Spring must not be wrapped: the proxy has no field state, so code
            // reading fields of the configuration directly (e.g. a non-static inner @Configuration accessing the
            // enclosing instance's fields through this$0) would get null, see #623. Configuration beans are
            // singletons destroyed together with their dependents on reload, so the proxy is not needed.
            if (isEnhancedConfiguration(bean.getClass())) {
                return bean;
            }

            return EnhancerProxyCreater.createProxy(beanFactry, bean, paramClasses, paramValues);
        }

        return bean;
    }

    /**
     * Checks by name, so it does not depend on the class loader the Spring classes were loaded by.
     */
    static boolean isEnhancedConfiguration(Class<?> beanClass) {
        for (Class<?> c = beanClass; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                if (ENHANCED_CONFIGURATION_CLASS_NAME.equals(iface.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}