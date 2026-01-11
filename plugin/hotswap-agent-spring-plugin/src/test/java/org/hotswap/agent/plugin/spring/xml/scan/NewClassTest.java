/*
 * Copyright 2013-2025 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.xml.scan;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-scan/scanContext.xml"})
public class NewClassTest {
    private static AgentLogger LOGGER = AgentLogger.getLogger(NewClassTest.class);
    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        BeanFactoryAssistant.getBeanFactoryAssistant(applicationContext.getBeanFactory()).reset();
    }

    @Test
    public void swapSingleClassTest() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        LOGGER.info("NewClassTest.swapSingleClassTest." + applicationContext.getBeanFactory());
        assertNotNull(applicationContext.getBean(ScanItem.class).getName());
        assertNotNull(applicationContext.getBean(ScanItem2.class).getName());

        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem1",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem1", ScanItem.class.getClassLoader());
        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem2",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem2", ScanItem.class.getClassLoader());

        Thread.sleep(8000);

        BaseTestUtil.waitForClassReloadsToFinish();

        LOGGER.info("swap class finished");
        assertNotNull(applicationContext.getBean(ScanItem.class).getName());
        assertNotNull(applicationContext.getBean(ScanItem2.class).getName());
        assertNotNull(applicationContext.getBean("scanBakItem1"));
        assertNotNull(applicationContext.getBean("scanBakItem2"));
    }

    private void moveClass(String origClassName, String targetClassName, ClassLoader cl) throws Exception {
        File file = new ClassPathResource("xml-scan/scan-item.properties").getFile();
        String path = file.getAbsolutePath();
        String currentPath = path;
        while(true) {
            path = file.getAbsolutePath();
            if (path.endsWith("target") || path.endsWith("target/")) {
                break;
            }
            currentPath = path;
            file = file.getParentFile();
        }
//
//        Class c = HotSwapper.newClass(targetClassName, currentPath, cl);
//        HotSwapper.swapClasses(c, origClassName);

        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(cl));
        CtClass ctClass = classPool.get(origClassName);

        ClassPathResource res = new ClassPathResource(targetClassName.replace('.', '/') + ".class");

        Path outFile = Paths.get(currentPath)
            .resolve(targetClassName.replace('.', '/') + ".class");

        Files.copy(new ByteArrayInputStream(ctClass.toBytecode()), outFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
