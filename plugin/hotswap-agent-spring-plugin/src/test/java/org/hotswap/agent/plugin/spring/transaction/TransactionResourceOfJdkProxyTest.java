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
package org.hotswap.agent.plugin.spring.transaction;

import org.hotswap.agent.plugin.spring.core.TransactionResourceMap;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.getbean.SpringHotswapAgentProxy;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A transaction resource (e.g. the connection) bound to a key (e.g. the data source) is still found when a reload
 * has changed the hash code of the key. Without it the commit fails with "No value for key [...] bound to thread",
 * see #287.
 */
public class TransactionResourceOfJdkProxyTest {

    /**
     * Takes the hash code from its target, like the handler of a Spring AOP proxy and most other ones. Two proxies
     * with the same handler are equal, like two Spring AOP proxies.
     */
    private static class TargetHandler implements InvocationHandler {
        Object target = new Object();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "hashCode":
                    return target.hashCode();
                case "equals":
                    return args[0] != null && Proxy.isProxyClass(args[0].getClass())
                            && Proxy.getInvocationHandler(args[0]) == this;
                default:
                    return "proxy of " + target;
            }
        }
    }

    public static class Service {
        static final AtomicInteger INSTANCES = new AtomicInteger();

        public Service() {
            INSTANCES.incrementAndGet();
        }
    }

    @Configuration
    public static class CglibProxyConfiguration {
        // a CGLIB proxy, which the agent wraps in its own proxy
        @Bean
        public ProxyFactoryBean service() {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(new Service());
            factory.setProxyTargetClass(true);
            return factory;
        }
    }

    private final TargetHandler handler = new TargetHandler();
    private final Object key = newProxy(handler);

    private static Object newProxy(InvocationHandler handler) {
        return Proxy.newProxyInstance(TransactionResourceOfJdkProxyTest.class.getClassLoader(),
                new Class<?>[]{Runnable.class}, handler);
    }

    @After
    public void unbind() {
        TransactionSynchronizationManager.unbindResourceIfPossible(key);
    }

    @Test
    public void resourceIsFoundAfterReloadHasChangedTheHashCodeOfItsKey() {
        TransactionSynchronizationManager.bindResource(key, "connection");
        int hashCode = key.hashCode();

        // what a reload does when it replaces the invocation handler of the proxy: it calls another target now
        handler.target = new Object();
        TransactionResourceMap.hashCodeChanged(key);

        assertFalse(hashCode == key.hashCode());
        assertTrue(TransactionSynchronizationManager.hasResource(key));
        assertEquals("connection", TransactionSynchronizationManager.getResource(key));
        assertEquals("connection", TransactionSynchronizationManager.unbindResource(key));
        assertFalse(TransactionSynchronizationManager.hasResource(key));
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    }

    @Test
    public void otherResourcesStayBoundWhenTheResourceOfChangedKeyIsUnbound() {
        Object otherKey = new Object();
        TransactionSynchronizationManager.bindResource(otherKey, "other");
        TransactionSynchronizationManager.bindResource(key, "connection");
        try {
            handler.target = new Object();
            TransactionResourceMap.hashCodeChanged(key);

            assertEquals("connection", TransactionSynchronizationManager.unbindResource(key));
            assertFalse(TransactionSynchronizationManager.hasResource(key));
            assertEquals("other", TransactionSynchronizationManager.getResource(otherKey));
        } finally {
            TransactionSynchronizationManager.unbindResource(otherKey);
        }
    }

    @Test
    public void resourceIsFoundByTheNewProxyOfTheBean() {
        TransactionSynchronizationManager.bindResource(key, "connection");
        handler.target = new Object();
        TransactionResourceMap.hashCodeChanged(key);
        // the proxy of the recreated bean, the old one has its invocation handler
        Object newProxy = newProxy(handler);

        assertEquals("connection", TransactionSynchronizationManager.getResource(newProxy));
    }

    @Test
    public void resourceIsNotFoundIfTheProxyItselfHasChangedItsHashCode() {
        TransactionSynchronizationManager.bindResource(key, "connection");

        // e.g. a proxy that calls the data source of the current tenant
        handler.target = new Object();

        try {
            assertNull(TransactionSynchronizationManager.getResource(key));
            try {
                TransactionSynchronizationManager.unbindResource(key);
                fail("the resource is bound to another hash code");
            } catch (IllegalStateException expected) {
                // No value for key bound to thread
            }
        } finally {
            // for the cleanup
            TransactionResourceMap.hashCodeChanged(key);
        }
    }

    @Test
    public void resourceOfAnotherProxyIsNotFound() {
        Object other = newProxy(new TargetHandler());
        TransactionSynchronizationManager.bindResource(key, "connection");
        handler.target = new Object();
        TransactionResourceMap.hashCodeChanged(key);

        assertNull(TransactionSynchronizationManager.getResource(other));
        try {
            TransactionSynchronizationManager.unbindResource(other);
            fail("the resource is bound to another key");
        } catch (IllegalStateException expected) {
            // No value for key bound to thread
        }
        assertEquals("connection", TransactionSynchronizationManager.getResource(key));
    }

    @Test
    public void resourceOfKeyThatIsNotAProxyIsFoundByItsHashCodeOnly() {
        List<String> plainKey = new ArrayList<>();
        TransactionSynchronizationManager.bindResource(plainKey, "connection");
        try {
            // changes the hash code of the list
            plainKey.add("changed");

            assertNull(TransactionSynchronizationManager.getResource(plainKey));
        } finally {
            plainKey.clear();
            TransactionSynchronizationManager.unbindResource(plainKey);
        }
    }

    @Test
    public void lookupOfAnotherKeyDoesNotCallTheProxyOfTheAgent() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                CglibProxyConfiguration.class);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        Object service = context.getBean("service");
        try {
            TransactionSynchronizationManager.bindResource(service, "connection");
            // the proxy of the agent would get the bean again when it is called
            ProxyReplacer.clearAllProxies();
            beanFactory.destroySingleton("service");
            int instances = Service.INSTANCES.get();

            assertNull(TransactionSynchronizationManager.getResource(new Object()));
            assertNull(TransactionSynchronizationManager.getResource(newProxy(new TargetHandler())));
            assertEquals(instances, Service.INSTANCES.get());

            // nor when its definition is gone and its equals() would fail
            beanFactory.removeBeanDefinition("service");
            assertNull(TransactionSynchronizationManager.getResource(new Object()));
        } finally {
            clearResources();
            SpringChangedAgent.destroyBeanFactory(beanFactory);
            context.close();
        }
    }

    // unbinds also a key whose hashCode() fails
    private static void clearResources() {
        try {
            java.lang.reflect.Field resources = TransactionSynchronizationManager.class.getDeclaredField("resources");
            resources.setAccessible(true);
            ((ThreadLocal<?>) resources.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void resourceBoundToProxyOfTheAgentIsFoundAfterItsBeanWasRecreated() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                CglibProxyConfiguration.class);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        Object service = context.getBean("service");
        try {
            assertTrue(service instanceof SpringHotswapAgentProxy);
            TransactionSynchronizationManager.bindResource(service, "connection");
            int hashCode = service.hashCode();

            // what a reload does when the bean is recreated: the proxy of the agent gets the new bean
            ProxyReplacer.clearAllProxies();
            beanFactory.destroySingleton("service");

            assertFalse(hashCode == service.hashCode());
            assertEquals("connection", TransactionSynchronizationManager.getResource(service));
            assertEquals("connection", TransactionSynchronizationManager.unbindResource(service));
            assertFalse(TransactionSynchronizationManager.hasResource(service));
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(service);
            SpringChangedAgent.destroyBeanFactory(beanFactory);
            context.close();
        }
    }
}
