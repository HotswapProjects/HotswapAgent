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

import javax.sql.DataSource;

import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/287 - a JDK proxy of a data source
 * (e.g. created by a profiling bean post processor) was wrapped by a HotswapAgent proxy implementing
 * InfrastructureProxy. Spring unwraps the transaction resource key through it, so after a reload that recreated the
 * data source during a transaction, the key changed and the commit failed with "No value for key [...] bound to
 * thread". The wrapper existed up to Java 15 only (see #737), so the test fails without the fix on Java 8 and 11.
 */
public class JdkProxyDataSourceTransactionTest {

    @Configuration
    public static class DataSourceConfiguration {

        @Bean
        public static BeanPostProcessor dataSourceProxyPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DataSource)) {
                        return bean;
                    }
                    Object target = bean;
                    return Proxy.newProxyInstance(DataSourceConfiguration.class.getClassLoader(),
                            new Class<?>[]{DataSource.class}, (proxy, method, args) -> method.invoke(target, args));
                }
            };
        }

        @Bean
        public DataSource dataSource() {
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(org.h2.Driver.class);
            dataSource.setUrl("jdbc:h2:mem:jdkProxyDataSourceTransactionTest;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            return dataSource;
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    private AnnotationConfigApplicationContext context;

    @Before
    public void setUp() {
        context = new AnnotationConfigApplicationContext(DataSourceConfiguration.class);
    }

    @After
    public void tearDown() {
        // unregister before close, so later reloads in other tests do not hit this closed context
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void commitAfterDataSourceRecreatedDuringTransaction() {
        DataSourceTransactionManager transactionManager = context.getBean(DataSourceTransactionManager.class);
        DataSource dataSource = transactionManager.getDataSource();
        assertTrue(Proxy.isProxyClass(dataSource.getClass()));

        Integer result = new TransactionTemplate(transactionManager).execute(status -> {
            Integer value = new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
            // what a reload does when the data source bean is recreated while the transaction runs
            ProxyReplacer.clearAllProxies();
            ((DefaultListableBeanFactory) context.getBeanFactory()).destroySingleton("dataSource");
            return value;
        });

        assertEquals(Integer.valueOf(1), result);
    }
}
