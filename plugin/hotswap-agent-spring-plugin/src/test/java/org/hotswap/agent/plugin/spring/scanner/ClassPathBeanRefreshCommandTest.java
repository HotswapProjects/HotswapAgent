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
package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.scanner.refresh.RefreshBean;
import org.hotswap.agent.util.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The refresh command must look up the changed class by its name, not by the base package (issue #732).
 */
public class ClassPathBeanRefreshCommandTest {

    private static final String BASE_PACKAGE = RefreshBean.class.getPackage().getName();

    private AnnotationConfigApplicationContext context;
    private DefaultListableBeanFactory beanFactory;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        context = new AnnotationConfigApplicationContext(BASE_PACKAGE);
        beanFactory = context.getDefaultListableBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);
    }

    @After
    public void after() {
        // the context is null if its start failed in before()
        if (context != null) {
            SpringChangedAgent.destroyBeanFactory(beanFactory);
            context.close();
        }
    }

    @Test
    public void loadsChangedClassByItsName() throws Exception {
        RecordingClassLoader classLoader = new RecordingClassLoader(getClass().getClassLoader());
        String className = RefreshBean.class.getName().replace('.', '/');
        byte[] classDefinition = IOUtils.toByteArray(
                getClass().getClassLoader().getResource(className + ".class").toURI());

        new ClassPathBeanRefreshCommand(classLoader, BASE_PACKAGE, className, classDefinition, null).executeCommand();

        assertTrue(classLoader.loaded.toString(), classLoader.loaded.contains(RefreshBean.class.getName()));
        assertFalse(classLoader.loaded.toString(), classLoader.loaded.contains(BASE_PACKAGE));
    }

    private static class RecordingClassLoader extends ClassLoader {
        final List<String> loaded = new ArrayList<>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loaded.add(name);
            return super.loadClass(name, resolve);
        }
    }
}
