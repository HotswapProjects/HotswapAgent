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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.sql.DataSource;

import org.hotswap.agent.plugin.spring.getbean.HotswapSpringInvocationHandler;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The proxy of the agent around a JDK proxy bean: a singleton has one, however it is asked for.
 */
public class JdkProxyWrapperTest {

    public static class Holder {
        final ReloadGreeter greeter;

        Holder(ReloadGreeter greeter) {
            this.greeter = greeter;
        }
    }

    @Configuration
    public static class WrapperConfiguration {
        @Bean
        public ReloadGreeterImpl greeterImpl() {
            return new ReloadGreeterImpl();
        }

        @Bean(name = {"greeter", "greeterAlias"})
        public ProxyFactoryBean greeter(ReloadGreeterImpl greeterImpl) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(greeterImpl);
            factory.setInterfaces(ReloadGreeter.class);
            return factory;
        }

        @Bean
        @Scope("prototype")
        public ReloadGreeter prototypeGreeter() {
            ProxyFactory factory = new ProxyFactory(new ReloadGreeterImpl());
            factory.setInterfaces(ReloadGreeter.class);
            return (ReloadGreeter) factory.getProxy();
        }

        @Bean
        public Holder holder(ReloadGreeter greeter) {
            return new Holder(greeter);
        }

        // not a proxy of Spring AOP
        @Bean
        public DataSource foreignProxy() {
            return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                    new ForeignHandler());
        }
    }

    public static class ForeignHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return method.getName().equals("hashCode") ? (Object) 1 : null;
        }
    }

    private AnnotationConfigApplicationContext context;

    @Before
    public void before() {
        context = new AnnotationConfigApplicationContext(WrapperConfiguration.class);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void singletonHasOneProxy() {
        Object greeter = context.getBean("greeter");

        assertSame(greeter, context.getBean("greeter"));
        assertSame(greeter, context.getBean("greeterAlias"));
        assertSame(greeter, context.getBean("greeter", ReloadGreeter.class));
        assertSame(greeter, context.getBean(Holder.class).greeter);
        assertSame(greeter, context.getBeansOfType(ReloadGreeter.class).get("greeter"));
        assertEquals(greeter, greeter);
        // the factory bean itself is not a proxy
        assertTrue(context.getBean("&greeter") instanceof ProxyFactoryBean);
    }

    @Test
    public void singletonOfParentHasOneProxy() {
        try (AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext()) {
            child.setParent(context);
            child.refresh();
            try {
                assertSame(context.getBean("greeter"), child.getBean("greeter"));
            } finally {
                SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) child.getBeanFactory());
            }
        }
    }

    @Test
    public void prototypesHaveTheirOwnProxies() {
        ReloadGreeter first = (ReloadGreeter) context.getBean("prototypeGreeter");
        ReloadGreeter second = (ReloadGreeter) context.getBean("prototypeGreeter");

        assertNotSame(first, second);
        assertNotEquals(first, second);
        assertNotEquals(first.greet(), second.greet());
        assertEquals(first.greet(), first.greet());
    }

    /**
     * Code of its library may cast its invocation handler.
     */
    @Test
    public void foreignJdkProxyIsNotWrapped() {
        Object foreign = context.getBean("foreignProxy");

        assertTrue(Proxy.getInvocationHandler(foreign) instanceof ForeignHandler);
        assertTrue(Proxy.getInvocationHandler(context.getBean("greeter")) instanceof HotswapSpringInvocationHandler);
    }
}
