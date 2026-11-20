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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxies the beans. The beans inside these proxies can be cleared.
 *
 * @author Erki Ehtla
 */
public class ProxyReplacer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyReplacer.class);
    private static Class<?> infrastructureProxyClass;
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
     * Spring creates a CGLIB subclass of a bean with lookup or replaced methods, its callbacks are inner classes of
     * CglibSubclassingInstantiationStrategy
     */
    private static final String METHOD_INJECTION_CALLBACK_PREFIX =
            "org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy$";

    /**
     * CGLIB bean classes checked by isMethodInjectionSubclass(), getBean() is called often
     */
    private static final Map<Class<?>, Boolean> METHOD_INJECTION_SUBCLASSES =
            Collections.synchronizedMap(new WeakHashMap<>());

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

        if (basePackagePrefixes != null) {
            boolean hasMatch = false;
            for (String basePackagePrefix : basePackagePrefixes) {
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

            // @Configuration classes enhanced by Spring must not be wrapped: the proxy has no field state, so code
            // reading fields of the configuration directly (e.g. a non-static inner @Configuration accessing the
            // enclosing instance's fields through this$0) would get null, see #623. Configuration beans are
            // singletons destroyed together with their dependents on reload, so the proxy is not needed.
            if (isEnhancedConfiguration(bean.getClass())) {
                return bean;
            }

            // The subclass Spring creates for lookup-method, @Lookup or replaced-method is not a proxy but the bean
            // itself and holds its fields. The proxy of HotswapAgent has no fields, so a final method or a direct field
            // access would see null, see #259. Like a plain bean, it is not wrapped.
            if (isMethodInjectionSubclass(bean)) {
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

    /**
     * Checks the callbacks of the CGLIB class by name, so it does not depend on the class loader the Spring classes
     * were loaded by, nor on the package of CGLIB (repackaged in Spring, or net.sf.cglib).
     */
    static boolean isMethodInjectionSubclass(Object bean) {
        Class<?> beanClass = bean.getClass();
        Boolean result = METHOD_INJECTION_SUBCLASSES.get(beanClass);
        if (result == null) {
            // Spring sets the same callbacks on every instance of the subclass, so the result is kept per class
            result = hasMethodInjectionCallback(bean);
            METHOD_INJECTION_SUBCLASSES.put(beanClass, result);
        }
        return result;
    }

    private static boolean hasMethodInjectionCallback(Object bean) {
        for (Class<?> iface : bean.getClass().getInterfaces()) {
            if (!iface.getName().endsWith(".cglib.proxy.Factory")) {
                continue;
            }
            try {
                Object[] callbacks = (Object[]) iface.getMethod("getCallbacks").invoke(bean);
                if (callbacks != null) {
                    for (Object callback : callbacks) {
                        if (callback != null && callback.getClass().getName().startsWith(METHOD_INJECTION_CALLBACK_PREFIX)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Unable to get the CGLIB callbacks of {}, it is wrapped by the proxy of HotswapAgent "
                        + "even if it has lookup or replaced methods", e, bean.getClass().getName());
            }
            return false;
        }
        return false;
    }

    private static Class<?> getInfrastructureProxyClass() throws ClassNotFoundException {
        if (infrastructureProxyClass == null) {
            infrastructureProxyClass = ProxyReplacer.class.getClassLoader().loadClass("org.springframework.core.InfrastructureProxy");
        }
        return infrastructureProxyClass;
    }
}