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
package org.hotswap.agent.plugin.spring.files;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLoggerHandler;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * The Spring plugin is notified about every changed XML file on the classpath, but only the files loaded
 * as bean definitions should reload the bean factory (issue #656).
 */
public class NonSpringXmlChangeTest {

    static final AtomicInteger postProcessorCalls = new AtomicInteger();

    public static class PostProcessorCounter implements BeanFactoryPostProcessor {
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            postProcessorCalls.incrementAndGet();
        }
    }

    // XML agents are registered once per path, so all tests use one context
    private static GenericXmlApplicationContext context;
    private static DefaultListableBeanFactory beanFactory;

    @BeforeClass
    public static void before() {
        BaseTestUtil.configMaxReloadTimes();
        context = new GenericXmlApplicationContext("classpath:non-spring-xml/context.xml");
        beanFactory = context.getDefaultListableBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
    }

    @AfterClass
    public static void after() {
        SpringChangedAgent.destroyBeanFactory(beanFactory);
        context.close();
    }

    @Test
    public void changedMapperXmlDoesNotReloadSpring() {
        int reloadTimes = reloadTimes();
        int calls = postProcessorCalls.get();

        List<String> warnings = collectWarnings(() -> reloadXml("non-spring-xml/TestMapper.xml"));

        assertEquals(new ArrayList<String>(), warnings);
        assertEquals(reloadTimes, reloadTimes());
        assertEquals(calls, postProcessorCalls.get());
    }

    @Test
    public void changedBeanDefinitionXmlReloadsSpring() {
        int reloadTimes = reloadTimes();
        int calls = postProcessorCalls.get();

        reloadXml("non-spring-xml/context.xml");

        assertEquals(reloadTimes + 1, reloadTimes());
        assertEquals(calls + 1, postProcessorCalls.get());
    }

    private void reloadXml(String path) {
        URL url = getClass().getClassLoader().getResource(path);
        SpringChangedAgent.addChangedXml(url);
        SpringChangedAgent.reload(System.currentTimeMillis());
    }

    private int reloadTimes() {
        return BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).getReloadTimes();
    }

    private static List<String> collectWarnings(Runnable action) {
        List<String> warnings = new ArrayList<>();
        AgentLoggerHandler originalHandler = AgentLogger.getHandler();
        AgentLogger.setHandler(new AgentLoggerHandler() {
            @Override
            public void print(Class clazz, AgentLogger.Level level, String message, Throwable throwable, Object... args) {
                if (level == AgentLogger.Level.WARNING || level == AgentLogger.Level.ERROR) {
                    warnings.add(message);
                }
                super.print(clazz, level, message, throwable, args);
            }
        });
        try {
            action.run();
        } finally {
            AgentLogger.setHandler(originalHandler);
        }
        return warnings;
    }
}
