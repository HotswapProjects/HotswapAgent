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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.core.InfrastructureProxy;

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
     * Bean factory -> name of a singleton -> its proxy of the agent, see {@link #wrapJdkProxy}
     */
    private static final Map<Object, Map<String, Reference<Object>>> jdkProxies =
            Collections.synchronizedMap(new WeakHashMap<Object, Map<String, Reference<Object>>>());

    /**
     * Invocation handler of the JDK proxies of Spring AOP
     */
    private static final String JDK_AOP_PROXY_CLASS_NAME = "org.springframework.aop.framework.JdkDynamicAopProxy";

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
        if (isSpringAopJdkProxy(bean)) {
            return wrapJdkProxy(beanFactry, bean, paramClasses, paramValues);
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

            return EnhancerProxyCreater.createProxy(beanFactry, bean, paramClasses, paramValues);
        }

        return bean;
    }

    /**
     * Wraps a JDK proxy bean in a proxy of the agent. A singleton keeps one proxy, also after a reload has created
     * the bean again: a reference kept by the application then calls the new bean, and the beans created later get
     * the same object.
     */
    private static Object wrapJdkProxy(Object beanFactory, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
        String name = paramValues.length > 0 && paramValues[0] instanceof String ? (String) paramValues[0] : null;
        if (name == null || BeanFactoryUtils.isFactoryDereference(name)) {
            return newJdkProxy(beanFactory, bean, paramClasses, paramValues);
        }
        // The factory that defines the bean: a child returns the bean of its parent without calling getBean() of
        // the parent, with the name its alias stands for.
        Object owner = beanFactory;
        while (owner instanceof AbstractBeanFactory) {
            name = ((AbstractBeanFactory) owner).canonicalName(name);
            if (((AbstractBeanFactory) owner).containsLocalBean(name)) {
                break;
            }
            owner = ((AbstractBeanFactory) owner).getParentBeanFactory();
        }
        if (!(owner instanceof AbstractBeanFactory) || !((AbstractBeanFactory) owner).isSingleton(name)) {
            // a prototype or a bean of a scope
            return newJdkProxy(beanFactory, bean, paramClasses, paramValues);
        }
        Map<String, Reference<Object>> proxies = jdkProxies.computeIfAbsent(owner, k -> new HashMap<>());
        synchronized (proxies) {
            Reference<Object> reference = proxies.get(name);
            Object proxy = reference == null ? null : reference.get();
            if (proxy != null && Arrays.asList(proxy.getClass().getInterfaces())
                    .containsAll(Arrays.asList(bean.getClass().getInterfaces()))) {
                ((HotswapSpringInvocationHandler) Proxy.getInvocationHandler(proxy)).setTarget(bean);
                return proxy;
            }
            // None yet, or the bean has other interfaces now. The proxy is shared, so it gets the bean again by its
            // name, not with the arguments of this call.
            proxy = newJdkProxy(owner, bean, new Class<?>[] {String.class}, new Object[] {name});
            proxies.put(name, new WeakReference<>(proxy));
            return proxy;
        }
    }

    private static Object newJdkProxy(Object beanFactory, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
        Set<Class<?>> interfaces = new LinkedHashSet<>(Arrays.asList(bean.getClass().getInterfaces()));
        interfaces.add(InfrastructureProxy.class);
        return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces.toArray(new Class<?>[0]),
                new HotswapSpringInvocationHandler(bean, beanFactory, paramClasses, paramValues));
    }

    /**
     * A JDK proxy of another library is not wrapped, its code may cast the invocation handler of the proxy.
     */
    private static boolean isSpringAopJdkProxy(Object bean) {
        return Proxy.isProxyClass(bean.getClass())
                && JDK_AOP_PROXY_CLASS_NAME.equals(Proxy.getInvocationHandler(bean).getClass().getName());
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