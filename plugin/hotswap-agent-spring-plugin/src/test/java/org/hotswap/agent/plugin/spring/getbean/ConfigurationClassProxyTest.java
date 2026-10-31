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

import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/623 - Spring-enhanced
 * {@code @Configuration} instances must not be wrapped by the HotswapAgent CGLIB proxy, otherwise an inner
 * configuration class reads the enclosing configuration's fields from the (field-less) proxy and gets null.
 */
public class ConfigurationClassProxyTest {

    @Test
    public void innerConfigurationReadsEnclosingConfigurationField() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.register(OuterConfigWithInnerConfig.class);
            context.refresh();
            OuterConfigWithInnerConfig.InnerConfigBean bean =
                    context.getBean(OuterConfigWithInnerConfig.InnerConfigBean.class);
            assertNotNull(bean.getEnvironmentClassName());
        } finally {
            close(context);
        }
    }

    @Test
    public void configurationClassIsNotWrappedByHotswapProxy() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.register(OuterConfigWithInnerConfig.class);
            context.refresh();
            Object config = context.getBean(OuterConfigWithInnerConfig.class);
            assertFalse(config.getClass().getName(), config instanceof SpringHotswapAgentProxy);
            assertSame(config, context.getBean(OuterConfigWithInnerConfig.class));
        } finally {
            close(context);
        }
    }

    /**
     * Unregister the bean factory from the agent before closing, otherwise later reloads in other tests
     * would try to reload this closed context and abort.
     */
    private static void close(AnnotationConfigApplicationContext context) {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }
}
