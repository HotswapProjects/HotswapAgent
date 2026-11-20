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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MethodReplacer;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for https://github.com/HotswapProjects/HotswapAgent/issues/259 - Spring creates a CGLIB subclass
 * of a bean with a lookup or replaced method. It is not an AOP proxy but the bean itself, with its fields. The HotswapAgent proxy
 * wrapping it has no fields, so a final method or a direct field access saw null.
 */
public class LookupMethodBeanProxyTest {

    public static class Dependency {
        public String name() {
            return "dependency";
        }
    }

    public static class Prototype {
    }

    public static class LookupBean {
        @Autowired
        Dependency dependency;

        @Lookup
        public Prototype createPrototype() {
            return null;
        }

        public final String finalName() {
            return dependency.name();
        }
    }

    public static class ReplacedMethodBean {
        @Autowired
        Dependency dependency;

        public String replaced() {
            return "original";
        }

        public final String finalName() {
            return dependency.name();
        }
    }

    public static class Replacer implements MethodReplacer {
        @Override
        public Object reimplement(Object obj, Method method, Object[] args) {
            return "replaced";
        }
    }

    public static class AdvisedBean {
        public String hello() {
            return "hello";
        }
    }

    @Aspect
    public static class AdvisedBeanAspect {
        @Around("execution(* org.hotswap.agent.plugin.spring.getbean.LookupMethodBeanProxyTest.AdvisedBean.hello(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            return pjp.proceed() + "WithAspect";
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class LookupConfiguration {
        @Bean
        public Dependency dependency() {
            return new Dependency();
        }

        @Bean
        @Scope("prototype")
        public Prototype prototype() {
            return new Prototype();
        }

        @Bean
        public Replacer replacer() {
            return new Replacer();
        }

        @Bean
        public AdvisedBeanAspect advisedBeanAspect() {
            return new AdvisedBeanAspect();
        }

        @Bean
        public AdvisedBean advisedBean() {
            return new AdvisedBean();
        }
    }

    private AnnotationConfigApplicationContext context;

    @Before
    public void setUp() {
        // LookupBean is registered as a class, a lookup method of a bean returned by a @Bean method is not used
        context = new AnnotationConfigApplicationContext();
        context.register(LookupConfiguration.class, LookupBean.class);
        // what <replaced-method> in XML defines
        RootBeanDefinition replacedMethodBean = new RootBeanDefinition(ReplacedMethodBean.class);
        replacedMethodBean.getMethodOverrides().addOverride(new ReplaceOverride("replaced", "replacer"));
        context.registerBeanDefinition("replacedMethodBean", replacedMethodBean);
        context.refresh();
    }

    @After
    public void tearDown() {
        // unregister before close, so later reloads in other tests do not hit this closed context
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
        context.close();
    }

    @Test
    public void lookupMethodBeanIsNotWrapped() {
        LookupBean bean = context.getBean(LookupBean.class);
        assertTrue(bean.getClass().getName(), EnhancerProxyCreater.isSupportedCglibProxy(bean));
        assertFalse(bean.getClass().getName(), bean instanceof SpringHotswapAgentProxy);
        assertNotNull(bean.dependency);
        assertEquals("dependency", bean.finalName());
        assertNotSame(bean.createPrototype(), bean.createPrototype());
    }

    @Test
    public void replacedMethodBeanIsNotWrapped() {
        ReplacedMethodBean bean = context.getBean(ReplacedMethodBean.class);
        assertTrue(bean.getClass().getName(), EnhancerProxyCreater.isSupportedCglibProxy(bean));
        assertFalse(bean.getClass().getName(), bean instanceof SpringHotswapAgentProxy);
        assertNotNull(bean.dependency);
        assertEquals("dependency", bean.finalName());
        assertEquals("replaced", bean.replaced());
    }

    @Test
    public void aopProxyIsWrapped() {
        AdvisedBean bean = context.getBean(AdvisedBean.class);
        assertTrue(bean.getClass().getName(), bean instanceof SpringHotswapAgentProxy);
        assertEquals("helloWithAspect", bean.hello());
    }
}
