package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.factorybean.bak.v11.BakXmlFactBean3;
import org.hotswap.agent.plugin.spring.factorybean.bak.v11.BakXmlFactBean4;
import org.hotswap.agent.plugin.spring.factorybean.bak.v11.BakXmlFactFactoryBean1;
import org.hotswap.agent.plugin.spring.factorybean.bak.v11.BakXmlFactFactoryBean2;
import org.hotswap.agent.plugin.spring.factorybean.bak.v21.V2BakXmlFactBean3;
import org.hotswap.agent.plugin.spring.factorybean.bak.v21.V2BakXmlFactBean4;
import org.hotswap.agent.plugin.spring.factorybean.bak.v21.V2BakXmlFactFactoryBean1;
import org.hotswap.agent.plugin.spring.factorybean.bak.v21.V2BakXmlFactFactoryBean2;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-factorybean/xml-factorybean.xml"})
public class XmlFactoryBeanChangeTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void testFactoryBeanChanged() throws Exception {
        System.out.println("XmlFactoryBeanChangeTest.testFactoryBeanChanged." + applicationContext.getBeanFactory());
        XmlFactBean1 xmlFactBean1 = applicationContext.getBean(XmlFactBean1.class);
        XmlFactBean2 xmlFactBean2 = applicationContext.getBean(XmlFactBean2.class);
        XmlFactBean3 xmlFactBean3 = applicationContext.getBean(XmlFactBean3.class);
        XmlFactBean4 xmlFactBean4 = applicationContext.getBean(XmlFactBean4.class);
        XmlFactBean5 xmlFactBean5 = applicationContext.getBean(XmlFactBean5.class);
        System.out.println("annotation1 : " + xmlFactBean1);
        System.out.println("annotation2 : " + xmlFactBean2);
        System.out.println("annotation3 : " + xmlFactBean3);
        System.out.println("annotation4 : " + xmlFactBean4);
        System.out.println("annotation5 : " + xmlFactBean5);
        Assert.assertEquals("XmlFactBean1", xmlFactBean1.getName());
        Assert.assertEquals("XmlFactBean2-v1", xmlFactBean2.getName());
        Assert.assertEquals("XmlFactBean3", xmlFactBean3.getName());
        Assert.assertEquals("XmlFactBean4", xmlFactBean4.getName());
        Assert.assertEquals("XmlFactBean5", xmlFactBean5.getName());
        XmlFactParentBean1 xmlFactParentBean1 = applicationContext.getBean(XmlFactParentBean1.class);
        XmlFactParentBean11 xmlFactParentBean11 = applicationContext.getBean(XmlFactParentBean11.class);
        XmlFactParentBean2 xmlFactParentBean2 = applicationContext.getBean(XmlFactParentBean2.class);
        XmlFactParentBean3 xmlFactParentBean3 = applicationContext.getBean(XmlFactParentBean3.class);
        XmlFactParentBean4 xmlFactParentBean4 = applicationContext.getBean(XmlFactParentBean4.class);
        XmlFactParentBean5 xmlFactParentBean5 = applicationContext.getBean(XmlFactParentBean5.class);
        Assert.assertEquals(xmlFactBean1, xmlFactParentBean1.getXmlFactBean1());
        Assert.assertEquals(xmlFactBean1, xmlFactParentBean11.getXmlFactBean1());
        Assert.assertEquals(xmlFactBean2, xmlFactParentBean2.getXmlFactBean2());
        Assert.assertEquals(xmlFactBean3, xmlFactParentBean3.getXmlFactBean3());
        Assert.assertEquals(xmlFactBean4, xmlFactParentBean4.getXmlFactBean4());
        Assert.assertEquals(xmlFactBean5, xmlFactParentBean5.getXmlFactBean5());
        // swap
        HotSwapper.swapClasses(XmlFactBean3.class, BakXmlFactBean3.class.getName());
        HotSwapper.swapClasses(XmlFactBean4.class, BakXmlFactBean4.class.getName());
        HotSwapper.swapClasses(XmlFactoryBean1.class, BakXmlFactFactoryBean1.class.getName());
        HotSwapper.swapClasses(XmlFactoryBean2.class, BakXmlFactFactoryBean2.class.getName());
        Thread.sleep(8000);
        // check
        XmlFactBean1 xmlFactBeanNew1 = applicationContext.getBean(XmlFactBean1.class);
        XmlFactBean2 xmlFactBeanNew2 = applicationContext.getBean(XmlFactBean2.class);
        XmlFactBean3 xmlFactBeanNew3 = applicationContext.getBean(XmlFactBean3.class);
        XmlFactBean4 xmlFactBeanNew4 = applicationContext.getBean(XmlFactBean4.class);
        XmlFactBean5 xmlFactBeanNew5 = applicationContext.getBean(XmlFactBean5.class);
        System.out.println("annotation1 changed: " + xmlFactBeanNew1);
        System.out.println("annotation2 changed: " + xmlFactBeanNew2);
        System.out.println("annotation3 changed: " + xmlFactBeanNew3);
        System.out.println("annotation4 changed: " + xmlFactBeanNew4);
        System.out.println("annotation5 changed: " + xmlFactBeanNew5);
        Assert.assertEquals("XmlFactBean1", xmlFactBeanNew1.getName());
        Assert.assertEquals("xml-beanfactory-name2", xmlFactBeanNew2.getName());
        Assert.assertEquals("XmlFactBean3-v1", xmlFactBeanNew3.getName());
        Assert.assertEquals("XmlFactBean4-v1", xmlFactBeanNew4.getName());
        Assert.assertEquals("XmlFactBean5", xmlFactBeanNew5.getName());
        XmlFactParentBean1 xmlFactParentBeanNew1 = applicationContext.getBean(XmlFactParentBean1.class);
        XmlFactParentBean11 xmlFactParentBeanNew11 = applicationContext.getBean(XmlFactParentBean11.class);
        XmlFactParentBean2 xmlFactParentBeanNew2 = applicationContext.getBean(XmlFactParentBean2.class);
        XmlFactParentBean3 xmlFactParentBeanNew3 = applicationContext.getBean(XmlFactParentBean3.class);
        XmlFactParentBean4 xmlFactParentBeanNew4 = applicationContext.getBean(XmlFactParentBean4.class);
        XmlFactParentBean5 xmlFactParentBeanNew5 = applicationContext.getBean(XmlFactParentBean5.class);
        Assert.assertEquals(xmlFactBeanNew1, xmlFactParentBeanNew1.getXmlFactBean1());
        Assert.assertEquals(xmlFactBeanNew1, xmlFactParentBeanNew11.getXmlFactBean1());
        Assert.assertEquals(xmlFactBeanNew2, xmlFactParentBeanNew2.getXmlFactBean2());
        Assert.assertEquals(xmlFactBeanNew3, xmlFactParentBeanNew3.getXmlFactBean3());
        Assert.assertEquals(xmlFactBeanNew4, xmlFactParentBeanNew4.getXmlFactBean4());
        Assert.assertEquals(xmlFactBeanNew5, xmlFactParentBeanNew5.getXmlFactBean5());

        Assert.assertNotEquals(xmlFactBeanNew1, xmlFactBean1);
        Assert.assertNotEquals(xmlFactBeanNew2, xmlFactBean2);
        Assert.assertNotEquals(xmlFactBeanNew3, xmlFactBean3);
        Assert.assertNotEquals(xmlFactBeanNew4, xmlFactBean4);
        Assert.assertEquals(xmlFactBeanNew5, xmlFactBean5);
        Assert.assertNotEquals(xmlFactParentBean1, xmlFactParentBeanNew1);
        Assert.assertNotEquals(xmlFactParentBean11, xmlFactParentBeanNew11);
        Assert.assertNotEquals(xmlFactParentBean2, xmlFactParentBeanNew2);
        Assert.assertNotEquals(xmlFactParentBean3, xmlFactParentBeanNew3);
        Assert.assertNotEquals(xmlFactParentBean4, xmlFactParentBeanNew4);
        Assert.assertEquals(xmlFactParentBean5, xmlFactParentBeanNew5);


        // swap v2
        HotSwapper.swapClasses(XmlFactBean3.class, V2BakXmlFactBean3.class.getName());
        HotSwapper.swapClasses(XmlFactBean4.class, V2BakXmlFactBean4.class.getName());
        HotSwapper.swapClasses(XmlFactoryBean1.class, V2BakXmlFactFactoryBean1.class.getName());
        HotSwapper.swapClasses(XmlFactoryBean2.class, V2BakXmlFactFactoryBean2.class.getName());
        Thread.sleep(8000);
        // check
        XmlFactBean1 xmlFactBeanV2_1 = applicationContext.getBean(XmlFactBean1.class);
        XmlFactBean2 xmlFactBeanV2_2 = applicationContext.getBean(XmlFactBean2.class);
        XmlFactBean3 xmlFactBeanV2_3 = applicationContext.getBean(XmlFactBean3.class);
        XmlFactBean4 xmlFactBeanV2_4 = applicationContext.getBean(XmlFactBean4.class);
        XmlFactBean5 xmlFactBeanV2_5 = applicationContext.getBean(XmlFactBean5.class);
        System.out.println("annotation1 changed v2: " + xmlFactBeanV2_1);
        System.out.println("annotation2 changed v2: " + xmlFactBeanV2_2);
        System.out.println("annotation3 changed v2: " + xmlFactBeanV2_3);
        System.out.println("annotation4 changed v2: " + xmlFactBeanV2_4);
        System.out.println("annotation5 changed v2: " + xmlFactBeanV2_5);
        Assert.assertEquals("XmlFactBean1", xmlFactBeanV2_1.getName());
        Assert.assertEquals("XmlFactBean2-v3", xmlFactBeanV2_2.getName());
        Assert.assertEquals("XmlFactBean3-v2", xmlFactBeanV2_3.getName());
        Assert.assertEquals("XmlFactBean4-v2", xmlFactBeanV2_4.getName());
        Assert.assertEquals("XmlFactBean5", xmlFactBeanV2_5.getName());
        XmlFactParentBean1 xmlFactParentBeanV2_1 = applicationContext.getBean(XmlFactParentBean1.class);
        XmlFactParentBean11 xmlFactParentBeanV2_11 = applicationContext.getBean(XmlFactParentBean11.class);
        XmlFactParentBean2 xmlFactParentBeanV2_2 = applicationContext.getBean(XmlFactParentBean2.class);
        XmlFactParentBean3 xmlFactParentBeanV2_3 = applicationContext.getBean(XmlFactParentBean3.class);
        XmlFactParentBean4 xmlFactParentBeanV2_4 = applicationContext.getBean(XmlFactParentBean4.class);
        XmlFactParentBean5 xmlFactParentBeanV2_5 = applicationContext.getBean(XmlFactParentBean5.class);
        Assert.assertEquals(xmlFactBeanV2_1, xmlFactParentBeanV2_1.getXmlFactBean1());
        Assert.assertEquals(xmlFactBeanV2_1, xmlFactParentBeanV2_11.getXmlFactBean1());
        Assert.assertEquals(xmlFactBeanV2_2, xmlFactParentBeanV2_2.getXmlFactBean2());
        Assert.assertEquals(xmlFactBeanV2_3, xmlFactParentBeanV2_3.getXmlFactBean3());
        Assert.assertEquals(xmlFactBeanV2_4, xmlFactParentBeanV2_4.getXmlFactBean4());
        Assert.assertEquals(xmlFactBeanV2_5, xmlFactParentBeanV2_5.getXmlFactBean5());

        Assert.assertNotEquals(xmlFactBeanNew1, xmlFactBeanV2_1);
        Assert.assertNotEquals(xmlFactBeanNew2, xmlFactBeanV2_2);
        Assert.assertNotEquals(xmlFactBeanNew3, xmlFactBeanV2_3);
        Assert.assertNotEquals(xmlFactBeanNew4, xmlFactBeanV2_4);
        Assert.assertEquals(xmlFactBeanNew5, xmlFactBeanV2_5);
        Assert.assertNotEquals(xmlFactParentBeanV2_1, xmlFactParentBeanNew1);
        Assert.assertNotEquals(xmlFactParentBeanV2_11, xmlFactParentBeanNew11);
        Assert.assertNotEquals(xmlFactParentBeanV2_2, xmlFactParentBeanNew2);
        Assert.assertNotEquals(xmlFactParentBeanV2_3, xmlFactParentBeanNew3);
        Assert.assertNotEquals(xmlFactParentBeanV2_4, xmlFactParentBeanNew4);
        Assert.assertEquals(xmlFactParentBeanV2_5, xmlFactParentBeanNew5);
    }
}