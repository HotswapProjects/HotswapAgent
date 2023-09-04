package org.hotswap.agent.plugin.spring.xml.annotationconfig;

import org.hotswap.agent.plugin.spring.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.SpringChangedHub;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AnnotationConfigTest {
    private static final Resource xmlFile = new ClassPathResource("xml-annotationconfig/annotation-config-Context.xml");
    private static final Resource xmlFileWithoutAnnotationConfig = new ClassPathResource("xml-annotationconfig/WithoutAnnotationConfig-context.xml");

    @Test
    public void swapXmlTest() throws Exception {
        AbstractApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:xml-annotationconfig/WithoutAnnotationConfig-context.xml");
        System.out.println("AnnotationConfigTest.swapXmlTest." + applicationContext.getBeanFactory());
        assertNull(applicationContext.getBean("item2", Item2.class).getName());

        byte[] content = Files.readAllBytes(xmlFileWithoutAnnotationConfig.getFile().toPath());
        try {
            Files.copy(xmlFile.getFile().toPath(), xmlFileWithoutAnnotationConfig.getFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() throws Exception {
                    return BeanFactoryAssistant.getBeanFactoryAssistant(applicationContext.getBeanFactory()).getReloadTimes() >= 1;
                }
            }, 11000));
            System.out.println("AnnotationConfigTest.swapXmlTest." + Arrays.asList(applicationContext.getBeanFactory().getBeanDefinitionNames()));
            System.out.println("AnnotationConfigTest.swapXmlTest." + Arrays.asList(Arrays.asList(applicationContext.getBeanFactory().getSingletonNames())));
            System.out.println("AnnotationConfigTest.swapXmlTest." + Arrays.asList(Arrays.asList(applicationContext.getBeanFactory().getSingleton("item2"))));
            assertNotNull(applicationContext.getBean("item2", Item2.class).getName());
        } finally {
            Files.write(xmlFileWithoutAnnotationConfig.getFile().toPath(), content);
            SpringChangedHub.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory()).setPause(true);
        }
    }
}
