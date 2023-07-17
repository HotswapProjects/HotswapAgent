package org.hotswap.agent.plugin.spring.xml.placeholder;

import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AnnotationConfigTest {
    private static final Resource xmlFile = new ClassPathResource("placeholderContext.xml");
    private static final Resource xmlFileWithoutAnnotationConfig = new ClassPathResource("placeholderContextWithoutAnnotationConfig.xml");

    @Test
    public void swapXmlTest() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:placeholderContextWithoutAnnotationConfig.xml");
        assertNull(applicationContext.getBean("item2", Item2.class).getName());

        XmlBeanDefinitionScannerAgent.reloadFlag = true;
        Files.copy(xmlFile.getFile().toPath(), xmlFileWithoutAnnotationConfig.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !XmlBeanDefinitionScannerAgent.reloadFlag;
            }
        }, 5000));

        assertNotNull(applicationContext.getBean("item2", Item2.class).getName());
    }
}
