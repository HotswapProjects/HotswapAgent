package org.hotswap.agent.plugin.spring.configuration;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.configuration.beans.Config;
import org.hotswap.agent.plugin.spring.configuration.configs.Config1;
import org.hotswap.agent.plugin.spring.configuration.configs.Config2;
import org.hotswap.agent.plugin.spring.configuration.configs.Config3;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
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
@ContextConfiguration(locations = {"classpath:configurations/configuration.xml"})
public class ConfigurationXmlTest {
    @Autowired
    private AbstractApplicationContext context;

    private static final Resource config = new ClassPathResource(Config.class.getName().replace('.', '/') + ".class");

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) context.getBeanFactory());
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) context.getBeanFactory());
    }

    @Test
    public void swapConfigClass() throws Exception {
        System.out.println("ConfigurationTest.swapConfigClass." + context.getBeanFactory());
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(Config.class.getClassLoader()));
        byte[] origClassBytes = classPool.getCtClass(Config.class.getName()).toBytecode();
        try {
            int reloadTimes = 1;
            // Config1.class -> Config.class
            replaceConfig(Config1.class, reloadTimes++);
            assertTrue(context.containsBean("a"));
            assertTrue(context.containsBean("b"));
            assertFalse(context.containsBean("c"));
            assertFalse(context.containsBean("A"));
//        Thread.sleep(60000);

            // Config2.class -> Config1.class
            replaceConfig(Config2.class, reloadTimes++);
            assertTrue(context.containsBean("a"));
            assertTrue(context.containsBean("c"));
            assertFalse(context.containsBean("b"));
            assertFalse(context.containsBean("A"));
//        Thread.sleep(60000);

            // Config3.class -> Config2.class
            replaceConfig(Config3.class, reloadTimes++);
            assertTrue(context.containsBean("A"));
            assertFalse(context.containsBean("a"));
            assertFalse(context.containsBean("b"));
            assertFalse(context.containsBean("c"));
        } finally {
            // recover Config.class
            Files.copy(new ByteArrayInputStream(origClassBytes), config.getFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void replaceConfig(Class<?> swap, int reloadTimes) throws Exception {
        long now = System.currentTimeMillis();
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(Config.class.getClassLoader()));
        CtClass ctClass = classPool.getAndRename(swap.getName(), Config.class.getName());

        Files.copy(new ByteArrayInputStream(ctClass.toBytecode()), config.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(context.getBeanFactory(), reloadTimes);
            }
        }, 8000));
        System.out.println("Reload times: " + reloadTimes + ", " + (System.currentTimeMillis() - now) + "ms");
    }
}

