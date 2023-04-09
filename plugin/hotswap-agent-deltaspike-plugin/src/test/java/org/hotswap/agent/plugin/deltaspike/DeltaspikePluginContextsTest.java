/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.deltaspike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.deltaspike.core.spi.scope.window.WindowContext;
import org.hotswap.agent.plugin.deltaspike.testBeans.WindowBean1;
import org.hotswap.agent.plugin.deltaspike.testBeansHotswap.WindowBean2;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Before;
import org.junit.Test;

/**
 * Test Deltaspike Plugin contexts, using OWB
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Vladimir Dvorak
 */
public class DeltaspikePluginContextsTest extends HAAbstractUnitTest {

    @SuppressWarnings("unchecked")
    public <T> T getBeanInstance(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        T result = beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
        return result;
    }

    @Before
    public void initContainer() {
        startContainer();
        WindowContext windowContext = getBeanInstance(WindowContext.class);
        windowContext.activateWindow("test");
    }

    @Test
    public void windowBeanTest() throws Exception {
        WindowBean1 windowBean = getBeanInstance(WindowBean1.class);
        assertEquals("WindowBean1.hello():ProxyHello1.hello()", windowBean.hello());
        swapClasses(WindowBean1.class, WindowBean2.class.getName());

        assertEquals("WindowBean2.hello():ProxyHello2.hello():ProxyHello1.hello()", windowBean.hello());

        // return configuration
        swapClasses(WindowBean1.class, WindowBean1.class.getName());
        assertEquals("WindowBean1.hello():ProxyHello1.hello()", windowBean.hello());
    }

    private void swapClasses(Class original, String swap) throws Exception {
        final Class<?> clazz;
        if (System.getProperty("cdicontainer.version").startsWith("weld")) {
            clazz = getClass().getClassLoader().loadClass("org.hotswap.agent.plugin.weld.command.BeanClassRefreshAgent");
        } else {
            clazz = getClass().getClassLoader().loadClass("org.hotswap.agent.plugin.owb.command.BeanClassRefreshCommand");
        }
        ReflectionHelper.set(null, clazz, "reloadFlag", true);
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                boolean reloadFlag = (boolean) ReflectionHelper.get(null, clazz, "reloadFlag");
                return !reloadFlag;
            }
        }, 1000));

        // TODO do not know why sleep is needed, maybe a separate thread in owb refresh?
        Thread.sleep(100);
    }
}