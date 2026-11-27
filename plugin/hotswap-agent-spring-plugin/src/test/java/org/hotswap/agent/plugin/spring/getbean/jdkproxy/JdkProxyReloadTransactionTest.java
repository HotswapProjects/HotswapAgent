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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;

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
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/287 with a JDK proxy. A reload
 * recreates a data source in a JDK proxy of Spring AOP while a transaction runs, the transaction must still find
 * its connection by the proxy.
 */
public class JdkProxyReloadTransactionTest {

    @Configuration
    public static class TransactionConfiguration {
        @Bean
        public ReloadDataSource targetDataSource() {
            return new ReloadDataSource();
        }

        // a JDK dynamic proxy of Spring AOP, its hashCode() is the one of its target
        @Bean
        public ProxyFactoryBean dataSource(ReloadDataSource targetDataSource) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(targetDataSource);
            factory.setInterfaces(DataSource.class);
            return factory;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
            return new JdbcTemplate(dataSource);
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
        assertTrue(Proxy.isProxyClass(dataSource.getClass()));

        Integer result = new TransactionTemplate(transactionManager).execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                swappingRule.swapClasses(ReloadDataSource.class, ReloadDataSourceBak.class, 1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            // the connection of the transaction is still found by the proxy
            assertTrue(TransactionSynchronizationManager.hasResource(dataSource));
            // the beans created again by the reload get the same proxy, and with it the connection of the transaction
            DataSource reloaded = context.getBean(JdbcTemplate.class).getDataSource();
            assertSame(dataSource, reloaded);
            assertSame(connection, DataSourceUtils.getConnection(reloaded));
            return context.getBean(JdbcTemplate.class).queryForObject("select 1", Integer.class);
        });

        assertEquals(Integer.valueOf(1), result);
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
        swappingRule.swapClasses(ReloadDataSource.class, ReloadDataSource.class, 2);
    }
}
