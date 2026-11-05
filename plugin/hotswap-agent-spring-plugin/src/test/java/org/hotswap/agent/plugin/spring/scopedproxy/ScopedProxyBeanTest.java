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
package org.hotswap.agent.plugin.spring.scopedproxy;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.scopedproxybak.ScopedProxyConfigurationBak;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * Change of a @Configuration class with a scoped proxy @Bean (#393).
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ScopedProxyTestConfiguration.class)
public class ScopedProxyBeanTest {

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
        swappingRule.setBeanFactory(beanFactory);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void changeConfigurationWithScopedProxyBean() throws Exception {
        assertEquals("aaaa", applicationContext.getBean(WebUser.class).web());
        assertEquals("plain-a", applicationContext.getBean("plainBean"));
        // a proxy kept by someone else, it looks up the target by name
        WebCls oldProxy = applicationContext.getBean("webCls", WebCls.class);

        swappingRule.swapClasses(ScopedProxyConfiguration.class, ScopedProxyConfigurationBak.class, 1);

        // the scoped target was "No bean named 'scopedTarget.webCls' available" before
        assertEquals("aaaa", applicationContext.getBean(WebUser.class).web());
        assertEquals("aaaa", oldProxy.getWeb());
        // a new scope (another thread here, a new session in a web application) uses the changed @Bean method
        assertEquals("bbbb", inNewThread(() -> applicationContext.getBean(WebUser.class).web()));
        assertEquals("plain-b", applicationContext.getBean("plainBean"));

        // and back again
        swappingRule.swapClasses(ScopedProxyConfiguration.class, ScopedProxyConfiguration.class, 2);
        assertEquals("aaaa", applicationContext.getBean(WebUser.class).web());
        assertEquals("aaaa", inNewThread(() -> applicationContext.getBean(WebUser.class).web()));
        assertEquals("plain-a", applicationContext.getBean("plainBean"));
    }

    private static String inNewThread(java.util.concurrent.Callable<String> callable) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable e) {
                result.set(e);
            }
        });
        thread.start();
        thread.join();
        if (result.get() instanceof Throwable) {
            throw new AssertionError("failed in new thread", (Throwable) result.get());
        }
        return (String) result.get();
    }
}
