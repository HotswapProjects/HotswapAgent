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

import java.util.Arrays;

import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Test;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.interceptor.TransactionalProxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/259 - a bean with a lookup method
 * must not be wrapped by the HotswapAgent CGLIB proxy, otherwise its final methods and the code reading its
 * fields get null from the (field-less) proxy.
 */
public class LookupMethodBeanTest {

    public static class Target {
    }

    @Test
    public void springProxyExtendedByAnotherInterfaceIsFound() {
        ProxyFactory proxyFactory = new ProxyFactory(new Target());
        proxyFactory.setProxyTargetClass(true);
        // e.g. TransactionProxyFactoryBean, Spring does not add SpringProxy itself then
        proxyFactory.addInterface(TransactionalProxy.class);
        Class<?> proxyClass = proxyFactory.getProxy().getClass();
        assertFalse(Arrays.asList(proxyClass.getInterfaces()).contains(SpringProxy.class));
        assertTrue(ProxyReplacer.implementsInterface(proxyClass, SpringProxy.class.getName()));
    }

    @Test
    public void beanWithLookupMethodKeepsItsFields() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.register(LookupMethodBean.class);
            context.refresh();
            LookupMethodBean bean = context.getBean(LookupMethodBean.class);
            assertFalse(bean.getClass().getName(), bean instanceof SpringHotswapAgentProxy);
            assertEquals("state", bean.finalState());
            assertTrue(bean.hasStateOf(bean.self()));
        } finally {
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
            context.close();
        }
    }
}
