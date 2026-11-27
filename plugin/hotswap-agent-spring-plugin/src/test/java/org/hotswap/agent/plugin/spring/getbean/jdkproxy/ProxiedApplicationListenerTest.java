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
package org.hotswap.agent.plugin.spring.getbean.jdkproxy;

import java.util.concurrent.atomic.AtomicInteger;

import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;

/**
 * Spring registers a listener bean itself and gets it again by its name, as the proxy of the agent: the proxy must be
 * equal to its bean, or the listener is called twice.
 */
public class ProxiedApplicationListenerTest {

    public static class TestEvent extends ApplicationEvent {
        public TestEvent(Object source) {
            super(source);
        }
    }

    public static class TestListener implements ApplicationListener<TestEvent> {
        final AtomicInteger events = new AtomicInteger();

        @Override
        @Transactional
        public void onApplicationEvent(TestEvent event) {
            events.incrementAndGet();
        }

        public int getEvents() {
            return events.get();
        }
    }

    public static class ListenerConfiguration {
        @Bean
        public ReloadDataSource dataSource() {
            return new ReloadDataSource();
        }

        @Bean
        public DataSourceTransactionManager transactionManager(ReloadDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public TestListener listener() {
            return new TestListener();
        }
    }

    @Configuration
    @EnableTransactionManagement
    public static class JdkProxyConfiguration extends ListenerConfiguration {
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class ClassProxyConfiguration extends ListenerConfiguration {
    }

    @Test
    public void jdkProxiedListenerIsCalledOnce() {
        assertEquals(1, publish(JdkProxyConfiguration.class));
    }

    @Test
    public void classProxiedListenerIsCalledOnce() {
        assertEquals(1, publish(ClassProxyConfiguration.class));
    }

    private static int publish(Class<?> configuration) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configuration)) {
            try {
                Object listener = context.getBean("listener");
                context.publishEvent(new TestEvent(context));
                // by reflection, the JDK proxy has only the interface of the listener
                return (Integer) org.springframework.aop.support.AopUtils.getTargetClass(listener)
                        .getMethod("getEvents").invoke(org.springframework.aop.framework.AopProxyUtils
                                .getSingletonTarget(listener));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            } finally {
                SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
            }
        }
    }
}
