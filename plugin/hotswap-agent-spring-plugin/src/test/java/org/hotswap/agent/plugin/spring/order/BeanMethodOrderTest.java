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
package org.hotswap.agent.plugin.spring.order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hotswap.agent.plugin.spring.order.beans.OrderedBeansConfiguration;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @Order on a @Bean method must sort the injected list. The scanned configuration is read with ASM, so the order
 * is only known from the resolved factory method.
 */
public class BeanMethodOrderTest {

    @Test
    public void injectedListIsSortedByOrderOfBeanMethods() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.scan("org.hotswap.agent.plugin.spring.order.beans");
            context.refresh();
            List<Integer> orders = new ArrayList<>();
            for (OrderedBeansConfiguration.OrderedBean bean
                    : context.getBean(OrderedBeansConfiguration.OrderedBeansUser.class).getOrderedBeans()) {
                orders.add(bean.getOrder());
            }
            Assert.assertEquals(Arrays.asList(1, 2, 100), orders);
        } finally {
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
            context.close();
        }
    }
}
