package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.xml.bak.factorymethod.BakFactoryMethodBean6;
import org.hotswap.agent.plugin.spring.xml.bak.factorymethod.BakFactoryMethodFactoryBean12;
import org.hotswap.agent.plugin.spring.xml.bak.factorymethod.BakFactoryMethodFactoryBean34;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Assert;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-factory-method/xml-factory-method.xml"})
public class FactoryMethodBeanChangeTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    private static final Resource propertyFile = new ClassPathResource("xml-factory-method/xml-factory-method-item.properties");
    private static final Resource changedPropertyFile = new ClassPathResource("xml-factory-method/xml-factory-method-item-change.properties");

    private static byte[] content;

    @Before
    public void before() throws IOException {
        BaseTestUtil.configMaxReloadTimes();
        content = Files.readAllBytes(propertyFile.getFile().toPath());
        System.out.println("FactoryMethodBeanChangeTest.before. " + applicationContext.getBeanFactory());
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @After
    public void tearDown() throws IOException {
        Files.write(propertyFile.getFile().toPath(), content);
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 3);
            }
        }, 11000));
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void testFactoryBeanChanged() throws Exception {
        System.out.println("FactoryMethodBeanChangeTest.testFactoryBeanChanged");
        FactoryMethodBean1 factoryMethodBean1 = applicationContext.getBean(FactoryMethodBean1.class);
        FactoryMethodBean2 factoryMethodBean2 = applicationContext.getBean(FactoryMethodBean2.class);
        FactoryMethodBean3 factoryMethodBean3 = applicationContext.getBean(FactoryMethodBean3.class);
        FactoryMethodBean4 factoryMethodBean4 = applicationContext.getBean(FactoryMethodBean4.class);
        FactoryMethodBean5 factoryMethodBean5 = applicationContext.getBean(FactoryMethodBean5.class);
        FactoryMethodBean6 factoryMethodBean6 = applicationContext.getBean(FactoryMethodBean6.class);
        System.out.println("factoryMethodBean : " + factoryMethodBean1);
        System.out.println("factoryMethodBean : " + factoryMethodBean2);
        System.out.println("factoryMethodBean : " + factoryMethodBean3);
        System.out.println("factoryMethodBean : " + factoryMethodBean4);
        System.out.println("factoryMethodBean : " + factoryMethodBean5);
        System.out.println("factoryMethodBean : " + factoryMethodBean6);
        Assert.assertEquals("factoryMethodBean-name1", factoryMethodBean1.getName());
        Assert.assertEquals("factoryMethodBean-name2-v1", factoryMethodBean2.getName());
        Assert.assertEquals("factoryMethodBean-name3-v1", factoryMethodBean3.getName());
        Assert.assertEquals("factoryMethodBean-name4-v1", factoryMethodBean4.getName());
        Assert.assertEquals("factoryMethodBean-name5-v1", factoryMethodBean5.getName());
        Assert.assertEquals("factoryMethodBean-name6-v1", factoryMethodBean6.getName());
        Assert.assertNull(factoryMethodBean6.getName2());
        FactoryMethodParentBean1 factoryMethodParentBean1 = applicationContext.getBean(FactoryMethodParentBean1.class);
        FactoryMethodParentBean2 factoryMethodParentBean2 = applicationContext.getBean(FactoryMethodParentBean2.class);
        FactoryMethodParentBean3 factoryMethodParentBean3 = applicationContext.getBean(FactoryMethodParentBean3.class);
        FactoryMethodParentBean4 factoryMethodParentBean4 = applicationContext.getBean(FactoryMethodParentBean4.class);
        FactoryMethodParentBean5 factoryMethodParentBean5 = applicationContext.getBean(FactoryMethodParentBean5.class);
        FactoryMethodParentBean6 factoryMethodParentBean6 = applicationContext.getBean(FactoryMethodParentBean6.class);
        Assert.assertEquals(factoryMethodBean1, factoryMethodParentBean1.getFactoryMethodBean1());
        Assert.assertEquals(factoryMethodBean2, factoryMethodParentBean2.getFactoryMethodBean2());
        Assert.assertEquals(factoryMethodBean3, factoryMethodParentBean3.getFactoryMethodBean3());
        Assert.assertEquals(factoryMethodBean4, factoryMethodParentBean4.getFactoryMethodBean4());
        Assert.assertEquals(factoryMethodBean5, factoryMethodParentBean5.getFactoryMethodBean5());
        Assert.assertEquals(factoryMethodBean6, factoryMethodParentBean6.getFactoryMethodBean6());
        // 1. swap properties only
        modifyPropertyFile();
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 1);
            }
        }, 11000));
        // check
        FactoryMethodBean1 factoryMethodBeanV2_1 = applicationContext.getBean(FactoryMethodBean1.class);
        FactoryMethodBean2 factoryMethodBeanV2_2 = applicationContext.getBean(FactoryMethodBean2.class);
        FactoryMethodBean3 factoryMethodBeanV2_3 = applicationContext.getBean(FactoryMethodBean3.class);
        FactoryMethodBean4 factoryMethodBeanV2_4 = applicationContext.getBean(FactoryMethodBean4.class);
        FactoryMethodBean5 factoryMethodBeanV2_5 = applicationContext.getBean(FactoryMethodBean5.class);
        FactoryMethodBean6 factoryMethodBeanV2_6 = applicationContext.getBean(FactoryMethodBean6.class);
        System.out.println("factoryMethod1 changed: " + factoryMethodBeanV2_1);
        System.out.println("factoryMethod2 changed: " + factoryMethodBeanV2_2);
        System.out.println("factoryMethod3 changed: " + factoryMethodBeanV2_3);
        System.out.println("factoryMethod4 changed: " + factoryMethodBeanV2_4);
        System.out.println("factoryMethod5 changed: " + factoryMethodBeanV2_5);
        System.out.println("factoryMethod5 changed: " + factoryMethodBeanV2_6);
        Assert.assertEquals("factoryMethodBean-name1", factoryMethodBeanV2_1.getName());
        Assert.assertEquals("factoryMethodBean-name2-v2", factoryMethodBeanV2_2.getName());
        Assert.assertEquals("factoryMethodBean-name3-v2", factoryMethodBeanV2_3.getName());
        Assert.assertEquals("factoryMethodBean-name4-v2", factoryMethodBeanV2_4.getName());
        Assert.assertEquals("factoryMethodBean-name5-v2", factoryMethodBeanV2_5.getName());
        Assert.assertEquals("factoryMethodBean-name6-v2", factoryMethodBeanV2_6.getName());
        Assert.assertNull(factoryMethodBeanV2_6.getName2());
        FactoryMethodParentBean1 factoryMethodParentBeanV2_1 = applicationContext.getBean(FactoryMethodParentBean1.class);
        FactoryMethodParentBean2 factoryMethodParentBeanV2_2 = applicationContext.getBean(FactoryMethodParentBean2.class);
        FactoryMethodParentBean3 factoryMethodParentBeanV2_3 = applicationContext.getBean(FactoryMethodParentBean3.class);
        FactoryMethodParentBean4 factoryMethodParentBeanV2_4 = applicationContext.getBean(FactoryMethodParentBean4.class);
        FactoryMethodParentBean5 factoryMethodParentBeanV2_5 = applicationContext.getBean(FactoryMethodParentBean5.class);
        FactoryMethodParentBean6 factoryMethodParentBeanV2_6 = applicationContext.getBean(FactoryMethodParentBean6.class);

        Assert.assertEquals(factoryMethodBeanV2_1, factoryMethodParentBeanV2_1.getFactoryMethodBean1());
        Assert.assertEquals(factoryMethodBeanV2_2, factoryMethodParentBeanV2_2.getFactoryMethodBean2());
        Assert.assertEquals(factoryMethodBeanV2_3, factoryMethodParentBeanV2_3.getFactoryMethodBean3());
        Assert.assertEquals(factoryMethodBeanV2_4, factoryMethodParentBeanV2_4.getFactoryMethodBean4());
        Assert.assertEquals(factoryMethodBeanV2_5, factoryMethodParentBeanV2_5.getFactoryMethodBean5());
        Assert.assertEquals(factoryMethodBeanV2_6, factoryMethodParentBeanV2_6.getFactoryMethodBean6());

        Assert.assertNotEquals(factoryMethodBeanV2_1, factoryMethodBean1);
        Assert.assertNotEquals(factoryMethodBeanV2_2, factoryMethodBean2);
        Assert.assertNotEquals(factoryMethodBeanV2_3, factoryMethodBean3);
        Assert.assertNotEquals(factoryMethodBeanV2_4, factoryMethodBean4);
        Assert.assertNotEquals(factoryMethodBeanV2_5, factoryMethodBean5);
        Assert.assertNotEquals(factoryMethodBeanV2_6, factoryMethodBean6);
        Assert.assertNotEquals(factoryMethodParentBean1, factoryMethodParentBeanV2_1);
        Assert.assertNotEquals(factoryMethodParentBean2, factoryMethodParentBeanV2_2);
        Assert.assertNotEquals(factoryMethodParentBean3, factoryMethodParentBeanV2_3);
        Assert.assertNotEquals(factoryMethodParentBean4, factoryMethodParentBeanV2_4);
        Assert.assertNotEquals(factoryMethodParentBean5, factoryMethodParentBeanV2_5);
        Assert.assertNotEquals(factoryMethodParentBean6, factoryMethodParentBeanV2_6);

        // swap class
        HotSwapper.swapClasses(FactoryMethodBean6.class, BakFactoryMethodBean6.class.getName());
        HotSwapper.swapClasses(FactoryMethodFactoryBean12.class, BakFactoryMethodFactoryBean12.class.getName());
        HotSwapper.swapClasses(FactoryMethodFactoryBean34.class, BakFactoryMethodFactoryBean34.class.getName());
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 2);
            }
        }, 110000));
        // check
        FactoryMethodBean1 factoryMethodBeanV3_1 = applicationContext.getBean(FactoryMethodBean1.class);
        FactoryMethodBean2 factoryMethodBeanV3_2 = applicationContext.getBean(FactoryMethodBean2.class);
        FactoryMethodBean3 factoryMethodBeanV3_3 = applicationContext.getBean(FactoryMethodBean3.class);
        FactoryMethodBean4 factoryMethodBeanV3_4 = applicationContext.getBean(FactoryMethodBean4.class);
        FactoryMethodBean5 factoryMethodBeanV3_5 = applicationContext.getBean(FactoryMethodBean5.class);
        FactoryMethodBean6 factoryMethodBeanV3_6 = applicationContext.getBean(FactoryMethodBean6.class);
        System.out.println("factoryMethod1 changed: " + factoryMethodBeanV3_1);
        System.out.println("factoryMethod2 changed: " + factoryMethodBeanV3_2);
        System.out.println("factoryMethod3 changed: " + factoryMethodBeanV3_3);
        System.out.println("factoryMethod4 changed: " + factoryMethodBeanV3_4);
        System.out.println("factoryMethod5 changed: " + factoryMethodBeanV3_5);
        System.out.println("factoryMethod6 changed: " + factoryMethodBeanV3_6);
        Assert.assertEquals("factoryMethodBean-name1", factoryMethodBeanV3_1.getName());
        Assert.assertEquals("hello:factoryMethodBean-name2-v2", factoryMethodBeanV3_2.getName());
        Assert.assertEquals("factoryMethodBean-name3-v2", factoryMethodBeanV3_3.getName());
        Assert.assertEquals("hello:factoryMethodBean-name4-v2", factoryMethodBeanV3_4.getName());
        Assert.assertEquals("factoryMethodBean-name5-v2", factoryMethodBeanV3_5.getName());
        Assert.assertEquals("hello:factoryMethodBean-name6-v2", factoryMethodBeanV3_6.getName());
        Assert.assertEquals("factoryMethodBean-name62-v2", factoryMethodBeanV3_6.getName2());
        FactoryMethodParentBean1 factoryMethodParentBeanV3_1 = applicationContext.getBean(FactoryMethodParentBean1.class);
        FactoryMethodParentBean2 factoryMethodParentBeanV3_2 = applicationContext.getBean(FactoryMethodParentBean2.class);
        FactoryMethodParentBean3 factoryMethodParentBeanV3_3 = applicationContext.getBean(FactoryMethodParentBean3.class);
        FactoryMethodParentBean4 factoryMethodParentBeanV3_4 = applicationContext.getBean(FactoryMethodParentBean4.class);
        FactoryMethodParentBean5 factoryMethodParentBeanV3_5 = applicationContext.getBean(FactoryMethodParentBean5.class);
        FactoryMethodParentBean6 factoryMethodParentBeanV3_6 = applicationContext.getBean(FactoryMethodParentBean6.class);

        Assert.assertEquals(factoryMethodBeanV3_1, factoryMethodParentBeanV3_1.getFactoryMethodBean1());
        Assert.assertEquals(factoryMethodBeanV3_2, factoryMethodParentBeanV3_2.getFactoryMethodBean2());
        Assert.assertEquals(factoryMethodBeanV3_3, factoryMethodParentBeanV3_3.getFactoryMethodBean3());
        Assert.assertEquals(factoryMethodBeanV3_4, factoryMethodParentBeanV3_4.getFactoryMethodBean4());
        Assert.assertEquals(factoryMethodBeanV3_5, factoryMethodParentBeanV3_5.getFactoryMethodBean5());
        Assert.assertEquals(factoryMethodBeanV3_6, factoryMethodParentBeanV3_6.getFactoryMethodBean6());

        Assert.assertNotEquals(factoryMethodBeanV2_1, factoryMethodBeanV3_1);
        Assert.assertNotEquals(factoryMethodBeanV2_2, factoryMethodBeanV3_2);
        Assert.assertNotEquals(factoryMethodBeanV2_3, factoryMethodBeanV3_3);
        Assert.assertNotEquals(factoryMethodBeanV2_4, factoryMethodBeanV3_4);
        Assert.assertEquals(factoryMethodBeanV2_5, factoryMethodBeanV3_5);
        Assert.assertNotEquals(factoryMethodBeanV2_6, factoryMethodBeanV3_6);
        Assert.assertNotEquals(factoryMethodParentBeanV3_1, factoryMethodParentBeanV2_1);
        Assert.assertNotEquals(factoryMethodParentBeanV3_2, factoryMethodParentBeanV2_2);
        Assert.assertNotEquals(factoryMethodParentBeanV3_3, factoryMethodParentBeanV2_3);
        Assert.assertNotEquals(factoryMethodParentBeanV3_4, factoryMethodParentBeanV2_4);
        Assert.assertEquals(factoryMethodParentBeanV3_5, factoryMethodParentBeanV2_5);
        Assert.assertNotEquals(factoryMethodParentBeanV3_6, factoryMethodParentBeanV2_6);
        // recover
        HotSwapper.swapClasses(FactoryMethodBean6.class, FactoryMethodBean6.class.getName());
    }

    private void modifyPropertyFile() throws Exception {
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void recoveryPropertyFile() throws Exception {
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}