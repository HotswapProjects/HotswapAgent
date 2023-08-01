package org.hotswap.agent.plugin.spring.configuration;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.plugin.spring.configuration.beans.Config;
import org.hotswap.agent.plugin.spring.configuration.configs.Config1;
import org.hotswap.agent.plugin.spring.configuration.configs.Config2;
import org.hotswap.agent.plugin.spring.configuration.configs.Config3;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:configuration.xml"})
public class ConfigurationXmlTest {
    @Autowired
    private ApplicationContext context;

    private static final Resource config = new ClassPathResource(Config.class.getName().replace('.', '/') + ".class");

    @Test
    public void swapConfigClass() throws Exception {
        // Config1.class -> Config.class
        replaceConfig(Config1.class);
        assertTrue(context.containsBean("a"));
        assertTrue(context.containsBean("b"));
        assertFalse(context.containsBean("c"));
        assertFalse(context.containsBean("A"));

        // Config2.class -> Config1.class
        replaceConfig(Config2.class);
        assertTrue(context.containsBean("a"));
        assertTrue(context.containsBean("c"));
        assertFalse(context.containsBean("b"));
        assertFalse(context.containsBean("A"));


        // Config3.class -> Config2.class
        replaceConfig(Config3.class);
        assertTrue(context.containsBean("A"));
        assertFalse(context.containsBean("a"));
        assertFalse(context.containsBean("b"));
        assertFalse(context.containsBean("c"));
    }

    private void replaceConfig(Class<?> swap) throws Exception {
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(Config.class.getClassLoader()));
        CtClass ctClass = classPool.getAndRename(swap.getName(), Config.class.getName());

        ClassPathBeanDefinitionScannerAgent.reloadFlag = true;
        Files.copy(new ByteArrayInputStream(ctClass.toBytecode()), config.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ClassPathBeanDefinitionScannerAgent.reloadFlag;
            }
        }, 3000));
    }
}

