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

import org.hotswap.agent.plugin.spring.getbean.JdkProxyRefresher;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * A kept JDK proxy of an interface that is not public calls the new bean, also if the new bean is a proxy of another
 * class and is called by reflection.
 */
public class JdkProxyOfHiddenInterfaceTest {

    interface HiddenGreeter {
        String greet();
    }

    @Configuration
    public static class HiddenInterfaceConfiguration {
        static int created;

        @Bean
        public Object hiddenGreeter() {
            final int instance = ++created;
            // the bean created again implements one more interface, so its proxy has another class
            Class<?>[] interfaces = instance == 1 ? new Class<?>[]{HiddenGreeter.class}
                    : new Class<?>[]{HiddenGreeter.class, Serializable.class};
            return Proxy.newProxyInstance(HiddenGreeter.class.getClassLoader(), interfaces, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return "hidden " + instance;
                }
            });
        }
    }

    @Test
    public void keptJdkProxyCallsNewBeanOfAnotherProxyClass() {
        HiddenInterfaceConfiguration.created = 0;
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                HiddenInterfaceConfiguration.class);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        try {
            HiddenGreeter greeter = (HiddenGreeter) context.getBean("hiddenGreeter");
            assertEquals("hidden 1", greeter.greet());

            // a reload of the changed bean
            beanFactory.destroySingleton("hiddenGreeter");
            beanFactory.preInstantiateSingletons();
            JdkProxyRefresher.refresh(beanFactory);

            assertEquals("hidden 2", greeter.greet());

            // the next reload, of another bean: the proxy already calls the bean
            InvocationHandler handler = Proxy.getInvocationHandler(greeter);
            JdkProxyRefresher.refresh(beanFactory);

            assertSame(handler, Proxy.getInvocationHandler(greeter));
            assertEquals("hidden 2", greeter.greet());
        } finally {
            SpringChangedAgent.destroyBeanFactory(beanFactory);
            context.close();
        }
    }
}
