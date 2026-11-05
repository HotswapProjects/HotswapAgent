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
package org.hotswap.agent.plugin.spring.qualifier;

import java.util.Collections;

import org.hotswap.agent.plugin.spring.qualifier.early.EarlyLookupPostProcessor;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * A @Bean method with a qualifier must be found by the qualifier also before the configuration is frozen.
 * The scanned configuration is read with ASM, so the qualifier is only known from the resolved factory method.
 */
public class QualifiedBeanEarlyLookupTest {

    @Test
    public void qualifiedFactoryMethodBeanIsFoundBeforeFreeze() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.scan("org.hotswap.agent.plugin.spring.qualifier.early");
            context.refresh();
            EarlyLookupPostProcessor postProcessor = context.getBean(EarlyLookupPostProcessor.class);
            Assert.assertEquals(Collections.singleton("qualifiedService"), postProcessor.getFoundBeanNames());
        } finally {
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
            context.close();
        }
    }
}
