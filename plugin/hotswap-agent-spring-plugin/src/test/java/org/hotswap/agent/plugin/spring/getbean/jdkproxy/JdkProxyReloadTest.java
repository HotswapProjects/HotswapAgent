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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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

        swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImpl.class, 2);

        assertEquals("v1 " + Instances.COUNT.get(), greeter.greet());
    }

    /**
     * A singleton has one proxy of the agent, also after a reload has created the bean again.
     */
    @Test
    public void singletonKeepsItsProxy() throws Exception {
        Object greeter = context.getBean("reloadGreeter");
        assertSame(greeter, context.getBean("reloadGreeter"));
        assertEquals(greeter, greeter);

        swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImplBak.class, 1);

        assertSame(greeter, context.getBean("reloadGreeter"));
        assertEquals(greeter, greeter);
        swappingRule.swapClasses(ReloadGreeterImpl.class, ReloadGreeterImpl.class, 2);
    }
}
