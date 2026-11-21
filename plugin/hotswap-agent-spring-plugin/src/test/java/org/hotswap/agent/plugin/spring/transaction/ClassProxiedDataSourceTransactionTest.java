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

import javax.sql.DataSource;

import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.getbean.SpringHotswapAgentProxy;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A transaction is committed after a reload has recreated its data source, if the data source is a CGLIB proxy
 * (e.g. it has an aspect), which the agent wraps in its own proxy. The transaction manager binds the connection to
 * that proxy, whose hash code comes from the bean. Without the lookup of the changed key the commit failed with
 * "No value for key [...] bound to thread", see #287.
 */
public class ClassProxiedDataSourceTransactionTest {

    @Configuration
    public static class DataSourceConfiguration {

        // a CGLIB proxy of the data source
        @Bean
        public ProxyFactoryBean dataSource() {
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(org.h2.Driver.class);
            dataSource.setUrl("jdbc:h2:mem:classProxiedDataSourceTransactionTest;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(dataSource);
            factory.setProxyTargetClass(true);
            return factory;
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    private AnnotationConfigApplicationContext context;
    private DefaultListableBeanFactory beanFactory;

    @Before
    public void setUp() {
        context = new AnnotationConfigApplicationContext(DataSourceConfiguration.class);
        beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    }

    @After
    public void tearDown() {
        // unregister before close, so later reloads in other tests do not hit this closed context
        SpringChangedAgent.destroyBeanFactory(beanFactory);
        context.close();
    }

    @Test
    public void commitAfterDataSourceRecreatedDuringTransaction() {
        DataSourceTransactionManager transactionManager = context.getBean(DataSourceTransactionManager.class);
        final DataSource dataSource = transactionManager.getDataSource();
        assertTrue(dataSource.getClass().getName(), dataSource instanceof SpringHotswapAgentProxy);

        Integer result = new TransactionTemplate(transactionManager).execute(status -> {
            int hashCode = dataSource.hashCode();
            // what a reload does when the data source bean is recreated while the transaction runs
            ProxyReplacer.clearAllProxies();
            beanFactory.destroySingleton("dataSource");
            assertFalse(hashCode == dataSource.hashCode());

            // the connection of the transaction is still used
            assertTrue(TransactionSynchronizationManager.hasResource(dataSource));
            return new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
        });

        assertEquals(Integer.valueOf(1), result);
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
    }
}
