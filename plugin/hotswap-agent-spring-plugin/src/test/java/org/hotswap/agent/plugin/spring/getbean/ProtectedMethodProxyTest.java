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

import java.lang.reflect.Method;

import org.hotswap.agent.plugin.spring.getbean.protectedmethod.ProtectedMethodBean;
import org.hotswap.agent.plugin.spring.getbean.protectedmethod.ProtectedMethodConfig;
import org.hotswap.agent.plugin.spring.getbean.protectedmethod.base.BaseBean;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/316 - a protected method of a bean
 * wrapped by the HotswapAgent CGLIB proxy, called by reflection (as Spring MVC calls a protected
 * {@code @ModelAttribute} method), failed with "IllegalArgumentException: Protected method" when the method is
 * declared in another package than the bean class.
 */
public class ProtectedMethodProxyTest {

    private AnnotationConfigApplicationContext context;

    @Before
    public void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(ProtectedMethodConfig.class);
        context.refresh();
    }

    @After
    public void tearDown() {
        // unregister before close, so later reloads in other tests do not hit this closed context
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void protectedMethodFromOtherPackage() throws Exception {
        Object bean = getProxiedBean();
        Method method = BaseBean.class.getDeclaredMethod("getName");
        method.setAccessible(true);
        assertEquals("base", method.invoke(bean));
    }

    @Test
    public void protectedMethodFromSamePackage() throws Exception {
        Object bean = getProxiedBean();
        Method method = ProtectedMethodBean.class.getDeclaredMethod("getOwnName");
        method.setAccessible(true);
        assertEquals("own", method.invoke(bean));
    }

    @Test
    public void publicMethodGoesThroughAspect() {
        assertEquals("helloWithAspect", getProxiedBean().hello());
    }

    private ProtectedMethodBean getProxiedBean() {
        ProtectedMethodBean bean = context.getBean(ProtectedMethodBean.class);
        assertTrue(bean.getClass().getName(), bean instanceof SpringHotswapAgentProxy);
        return bean;
    }
}
