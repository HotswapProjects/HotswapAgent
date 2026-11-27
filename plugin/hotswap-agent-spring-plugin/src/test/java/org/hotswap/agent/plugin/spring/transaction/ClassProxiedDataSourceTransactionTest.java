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

import java.lang.reflect.Field;

import javax.sql.DataSource;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/287 with a class-based proxy. A reload
 * recreates a data source in a CGLIB proxy while a transaction runs. The transaction manager got the proxy
 * of the agent around it, which takes its hash code from the bean, the transaction must still find its connection.
 */
public class ClassProxiedDataSourceTransactionTest {

    @Configuration
    public static class TransactionConfiguration {
        @Bean
        public ClassProxiedDataSource targetDataSource() {
            return new ClassProxiedDataSource();
        }

        // a class-based proxy of Spring AOP, e.g. of an aspect with proxyTargetClass
        @Bean
        public ProxyFactoryBean dataSource(ClassProxiedDataSource targetDataSource) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(targetDataSource);
            factory.setProxyTargetClass(true);
            return factory;
        }

        @Bean
        public DataSourceTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    private AnnotationConfigApplicationContext context;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        context = new AnnotationConfigApplicationContext(TransactionConfiguration.class);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
        swappingRule.setBeanFactory(beanFactory);
    }

    @After
    public void after() throws Exception {
        // a failed test may leave the resource bound, which a key that has changed its hash code cannot unbind
        Field resources = TransactionSynchronizationManager.class.getDeclaredField("resources");
        resources.setAccessible(true);
        ((ThreadLocal<?>) resources.get(null)).remove();
        TransactionSynchronizationManager.clear();
        // unregister before close, so later reloads in other tests do not hit this closed context
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void commitAfterReloadDuringTransaction() throws Exception {
        DataSourceTransactionManager transactionManager = context.getBean(DataSourceTransactionManager.class);
        DataSource dataSource = transactionManager.getDataSource();
        assertTrue(dataSource.getClass().getName().contains("$HOTSWAPAGENT_"));
        assertSame(dataSource, context.getBean("dataSource"));
        assertEquals(dataSource, dataSource);

        Integer result = new TransactionTemplate(transactionManager).execute(status -> {
            try {
                swappingRule.swapClasses(ClassProxiedDataSource.class, ClassProxiedDataSourceBak.class, 1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            // the connection of the transaction is still found by the proxy
            assertTrue(TransactionSynchronizationManager.hasResource(dataSource));
            return new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
        });

        assertEquals(Integer.valueOf(1), result);
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
        swappingRule.swapClasses(ClassProxiedDataSource.class, ClassProxiedDataSource.class, 2);
    }
}
