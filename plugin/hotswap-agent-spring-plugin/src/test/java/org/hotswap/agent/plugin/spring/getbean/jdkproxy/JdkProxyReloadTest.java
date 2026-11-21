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

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

/**
 * A JDK proxy bean kept by the application calls the new bean after a reload, on every Java version (#737).
 */
public class JdkProxyReloadTest {

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    private AnnotationConfigApplicationContext context;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        context = new AnnotationConfigApplicationContext(JdkProxyReloadConfiguration.class);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
        swappingRule.setBeanFactory(beanFactory);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void keptJdkProxyCallsNewBean() throws Exception {
        ReloadGreeter greeter = (ReloadGreeter) context.getBean("reloadGreeter");
        assertEquals("v1 " + Instances.COUNT.get(), greeter.greet());

        swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImplBak.class, 1);

        // the bean was created again, and the kept proxy calls it
        assertEquals("v2 " + Instances.COUNT.get(), greeter.greet());
        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(greeter).getClass().getName());

        swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImpl.class, 2);

        assertEquals("v1 " + Instances.COUNT.get(), greeter.greet());
    }

    @Test
    public void keptJdkProxyDoesNotCallDestroyedBeanAfterFailedReload() throws Exception {
        ReloadGreeter greeter = (ReloadGreeter) context.getBean("reloadGreeter");
        int instances = Instances.COUNT.get();
        assertEquals("v1 " + instances, greeter.greet());

        Instances.failReload = true;
        try {
            swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImplBak.class, 1);
        } finally {
            Instances.failReload = false;
        }

        // the reload destroyed the bean and failed before it created the new one, the proxy gets it when called
        assertEquals("v2 " + (instances + 1), greeter.greet());
        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(greeter).getClass().getName());
    }
}
