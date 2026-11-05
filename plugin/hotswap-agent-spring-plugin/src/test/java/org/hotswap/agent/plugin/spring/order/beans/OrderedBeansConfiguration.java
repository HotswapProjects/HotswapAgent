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
package org.hotswap.agent.plugin.spring.order.beans;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class OrderedBeansConfiguration {

    @Order(2)
    @Bean
    public OrderedBean orderTwo() {
        return new OrderedBean(2);
    }

    @Bean
    public OrderedBean orderLast() {
        return new OrderedBean(100);
    }

    @Order(1)
    @Bean
    public OrderedBean orderOne() {
        return new OrderedBean(1);
    }

    public static class OrderedBean {
        private final int order;

        public OrderedBean(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    @Configuration
    public static class OrderedBeansUser {
        @Autowired
        private List<OrderedBean> orderedBeans;

        public List<OrderedBean> getOrderedBeans() {
            return orderedBeans;
        }
    }
}
