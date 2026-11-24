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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLoggerHandler;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A successful reload must not end with the "no change" message, which belongs only to a skipped reload (issue #746).
 */
public class ReloadLogTest {

    public static class ReloadLogBean {
    }

    public static class NotABean {
    }

    private AnnotationConfigApplicationContext context;
    private DefaultListableBeanFactory beanFactory;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        context = new AnnotationConfigApplicationContext(ReloadLogBean.class);
        beanFactory = context.getDefaultListableBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory(beanFactory);
        context.close();
    }

    @Test
    public void changedBeanClassLogsReloadNotNoChange() {
        Object bean = context.getBean(ReloadLogBean.class);

        List<String> logs = collectLogs(() -> reload(ReloadLogBean.class));

        assertNotSame(bean, context.getBean(ReloadLogBean.class));
        assertEquals(1, logs.size());
        String log = logs.get(0);
        assertTrue(log, log.startsWith("RELOAD Spring beans of '{}' reloaded"));
        assertTrue(log, log.endsWith("[[reloadLogTest.ReloadLogBean], []]"));
    }

    @Test
    public void changedNonBeanClassLogsNoChange() {
        Object bean = context.getBean(ReloadLogBean.class);

        List<String> logs = collectLogs(() -> reload(NotABean.class));

        assertSame(bean, context.getBean(ReloadLogBean.class));
        assertEquals(Arrays.asList("INFO no change, ignore reloading '{}' []"), logs);
    }

    private void reload(Class<?> clazz) {
        SpringChangedAgent.addChangedClass(clazz, beanFactory);
        SpringChangedAgent.reload(System.currentTimeMillis());
    }

    /**
     * The INFO and RELOAD messages about this test's bean factory: level, message and the arguments after the
     * bean factory.
     */
    private List<String> collectLogs(Runnable action) {
        String beanFactoryName = ObjectUtils.identityToString(beanFactory);
        List<String> logs = new ArrayList<>();
        AgentLoggerHandler originalHandler = AgentLogger.getHandler();
        AgentLogger.setHandler(new AgentLoggerHandler() {
            @Override
            public void print(Class clazz, AgentLogger.Level level, String message, Throwable throwable, Object... args) {
                if ((level == AgentLogger.Level.INFO || level == AgentLogger.Level.RELOAD)
                        && args.length > 0 && beanFactoryName.equals(args[0])) {
                    String text = level + " " + message + " "
                            + Arrays.deepToString(Arrays.copyOfRange(args, 1, args.length));
                    logs.add(text);
                }
                super.print(clazz, level, message, throwable, args);
            }
        });
        try {
            action.run();
        } finally {
            AgentLogger.setHandler(originalHandler);
        }
        return logs;
    }
}
