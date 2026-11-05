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
package org.hotswap.agent.plugin.spring.lookup;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.lookup.beans.LookupBean;
import org.hotswap.agent.plugin.spring.lookup.beansbak.LookupBeanBak;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * An abstract bean with a @Lookup method must start and keep its lookup method after it is reloaded.
 */
public class LookupMethodTest {

    @Test
    public void lookupMethodWorksAfterReload() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        try {
            context.scan("org.hotswap.agent.plugin.spring.lookup.beans");
            context.refresh();
            LookupBean bean = context.getBean(LookupBean.class);
            Assert.assertEquals("lookup", bean.name());
            Assert.assertNotNull(bean.prototype());

            HotSwapper.swapClasses(LookupBean.class, LookupBeanBak.class.getName());
            Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() throws Exception {
                    return BaseTestUtil.finishReloading(beanFactory, 1);
                }
            }, 11000));

            LookupBean reloaded = context.getBean(LookupBean.class);
            Assert.assertNotSame(bean, reloaded);
            Assert.assertEquals("lookup-changed", reloaded.name());
            Assert.assertNotNull(reloaded.prototype());
        } finally {
            HotSwapper.swapClasses(LookupBean.class, LookupBean.class.getName());
            SpringChangedAgent.destroyBeanFactory(beanFactory);
            context.close();
        }
    }
}
