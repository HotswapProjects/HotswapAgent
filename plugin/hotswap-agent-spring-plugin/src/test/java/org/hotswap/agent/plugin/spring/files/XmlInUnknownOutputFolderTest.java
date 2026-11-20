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

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A bean definition XML file in an output folder unknown to the plugin, e.g. Eclipse bin, is reloaded (#397).
 */
public class XmlInUnknownOutputFolderTest {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n"
            + "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "       xsi:schemaLocation=\"http://www.springframework.org/schema/beans "
            + "http://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
            + "    <bean id=\"binBean\" class=\"java.lang.Object\"/>\n"
            + "</beans>\n";

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    // XML agents are registered once per path, so all tests use the same contexts
    private static URLClassLoader classLoader;
    // the registry of the XML reader is the context, its agent is kept in the global map
    private static GenericXmlApplicationContext context;
    private static DefaultListableBeanFactory beanFactory;
    private static URL xmlUrl;
    // the registry of the XML reader is the bean factory, its agent is kept in a map of the bean factory
    private static ClassPathXmlApplicationContext factoryContext;
    private static DefaultListableBeanFactory factoryBeanFactory;
    private static URL factoryXmlUrl;

    @BeforeClass
    public static void before() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        File bin = temporaryFolder.newFolder("my project", "bin");
        xmlUrl = writeXml(bin, "unknown-output-folder/context.xml");
        factoryXmlUrl = writeXml(bin, "unknown-output-folder/factory-context.xml");

        classLoader = new URLClassLoader(new URL[]{bin.toURI().toURL()}, XmlInUnknownOutputFolderTest.class.getClassLoader());
        context = new GenericXmlApplicationContext();
        context.setClassLoader(classLoader);
        context.load("classpath:unknown-output-folder/context.xml");
        context.refresh();
        beanFactory = context.getDefaultListableBeanFactory();
        SpringChangedAgent.getInstance(beanFactory);

        factoryContext = new ClassPathXmlApplicationContext(
                new String[]{"classpath:unknown-output-folder/factory-context.xml"}, false);
        factoryContext.setClassLoader(classLoader);
        factoryContext.refresh();
        factoryBeanFactory = (DefaultListableBeanFactory) factoryContext.getBeanFactory();
        SpringChangedAgent.getInstance(factoryBeanFactory);
    }

    private static URL writeXml(File folder, String path) throws Exception {
        File xml = new File(folder, path);
        xml.getParentFile().mkdirs();
        Files.write(xml.toPath(), XML.getBytes(StandardCharsets.UTF_8));
        return xml.toURI().toURL();
    }

    @AfterClass
    public static void after() throws Exception {
        SpringChangedAgent.destroyBeanFactory(beanFactory);
        context.close();
        SpringChangedAgent.destroyBeanFactory(factoryBeanFactory);
        factoryContext.close();
        classLoader.close();
    }

    @Test
    public void changedXmlReloadsSpring() {
        assertTrue(XmlBeanDefinitionScannerAgent.isBeanDefinitionXml(beanFactory, xmlUrl));

        int reloadTimes = reloadTimes();
        SpringChangedAgent.addChangedXml(xmlUrl);
        SpringChangedAgent.reload(System.currentTimeMillis());

        assertEquals(reloadTimes + 1, reloadTimes());
    }

    @Test
    public void changedXmlOfBeanFactoryRegistryReloadsSpring() {
        assertTrue(XmlBeanDefinitionScannerAgent.isBeanDefinitionXml(factoryBeanFactory, factoryXmlUrl));

        int reloadTimes = reloadTimes(factoryBeanFactory);
        SpringChangedAgent.addChangedXml(factoryXmlUrl);
        SpringChangedAgent.reload(System.currentTimeMillis());

        assertEquals(reloadTimes + 1, reloadTimes(factoryBeanFactory));
    }

    @Test
    public void beansAreRegisteredWithTheXml() {
        Set<String> beanNames = XmlBeanDefinitionScannerAgent.reloadXmls(beanFactory,
                Collections.singleton(xmlUrl), Collections.<String>emptySet());

        assertEquals(Collections.singleton("binBean"), beanNames);
    }

    private int reloadTimes() {
        return reloadTimes(beanFactory);
    }

    private int reloadTimes(DefaultListableBeanFactory beanFactory) {
        return BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).getReloadTimes();
    }
}
