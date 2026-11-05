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
package org.hotswap.agent.plugin.spring.events;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.eventsbak.EventBeanFirstListener;
import org.hotswap.agent.plugin.spring.eventsbak.EventBeanSecondListener;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
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

import static org.junit.Assert.assertEquals;

/**
 * @EventListener methods of a reloaded bean (#245).
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EventListenerConfiguration.class)
public class EventListenerReloadTest {

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
        EventCounters.first = 0;
        EventCounters.second = 0;
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void listenerOfReloadedBean() throws Exception {
        applicationContext.publishEvent(new TestEvent());
        assertEquals(0, EventCounters.first);

        // a listener method is added to a bean without listeners
        swappingRule.swapClasses(EventBean.class, EventBeanFirstListener.class, 1);
        assertEquals("first listener", applicationContext.getBean(EventBean.class).name());
        applicationContext.publishEvent(new TestEvent());
        assertEquals(1, EventCounters.first);

        // the listener method is replaced, the old one must not be called and the new one only once
        swappingRule.swapClasses(EventBean.class, EventBeanSecondListener.class, 2);
        applicationContext.publishEvent(new TestEvent());
        assertEquals(1, EventCounters.first);
        assertEquals(1, EventCounters.second);
    }
}
