/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.testBeans.*;
import org.hotswap.agent.util.spring.io.resource.ClassPathResource;
import org.hotswap.agent.util.spring.io.resource.Resource;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Hotswap class files of spring beans.
 * <p>
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Jiri Bubnik
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
public class SpringPlugin2Test {

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();
    private static AbstractApplicationContext xmlApplicationContext;
    private static Resource xmlContext = new ClassPathResource("xmlContext.xml");
    private static Resource xmlContextWithRepo = new ClassPathResource("xmlContextWithRepository.xml");
    private static Resource xmlContextWithChangedRepo = new ClassPathResource("xmlContextWithChangedRepository.xml");

    /*
     * -------Xml test, has to be put in there so xmlContext is load after component
     * scan context----------- (because we don't support multiple component-scan
     * context in single app, or ClassPathBeanDefinitionScannerAgent will not be
     * able to get the right registry to use (it will only use the first one it
     * encountered, Spring tends to reuse ClassPathScanner))
     */
    @Before
    public void before() throws IOException {
        BaseTestUtil.configMaxReloadTimes();
        if (xmlApplicationContext == null) {
            writeRepositoryToXml();
            xmlApplicationContext = new ClassPathXmlApplicationContext("xmlContext.xml");
        }
        swappingRule.setBeanFactory(xmlApplicationContext.getBeanFactory());
        System.out.println("SpringPlugin2Test.before." + xmlApplicationContext.getBeanFactory());
//        SpringChangedAgent.getInstance((DefaultListableBeanFactory) xmlApplicationContext.getBeanFactory());
    }

    @After
    public void after() throws IOException {
//        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) xmlApplicationContext.getBeanFactory());
    }

    private void writeRepositoryToXml() throws IOException {
        Files.copy(xmlContextWithRepo.getFile().toPath(), xmlContext.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeChangedRepositoryToXml() throws IOException {
        Files.copy(xmlContextWithChangedRepo.getFile().toPath(), xmlContext.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void swapXmlTest() throws IOException {
        System.out.println("SpringPlugin2Test.swapXmlTest");
        byte[] content = Files.readAllBytes(xmlContext.getFile().toPath());
        try {
            BeanService beanService = xmlApplicationContext.getBean("beanService", BeanService.class);
            Assert.assertEquals(beanService.hello(), "Hello from Repository ServiceWithAspect");

            writeChangedRepositoryToXml();
            assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() throws Exception {
                    return BaseTestUtil.finishReloading(xmlApplicationContext.getBeanFactory(), 1);
                }
            }, 10000));

            Assert.assertEquals(beanService.hello(), "Hello from ChangedRepository ServiceWithAspect");

            writeRepositoryToXml();
            assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() throws Exception {
                    return BaseTestUtil.finishReloading(xmlApplicationContext.getBeanFactory(), 2);
                }
            }, 10000));
            Assert.assertEquals(beanService.hello(), "Hello from Repository ServiceWithAspect");
        } finally {
            Files.write(xmlContext.getFile().toPath(), content);
        }
    }

}
