package org.hotswap.agent.plugin.spring.xml.constructor;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.xml.bak.constructor.v1.BakConstructorBean3;
import org.hotswap.agent.plugin.spring.xml.bak.constructor.v1.BakConstructorBean4;
import org.hotswap.agent.plugin.spring.xml.bak.constructor.v1.BakConstructorFactoryBean2;
import org.hotswap.agent.plugin.spring.xml.bak.constructor.v2.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-constructor/xml-constructor.xml"})
public class XmlConstructorBeanChangeTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Test
    public void testFactoryBeanChanged() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        System.out.println("XmlConstructorBeanChangeTest.testFactoryBeanChanged." + applicationContext.getBeanFactory());
        XmlConstructorBean1 xmlConstructorBean1 = applicationContext.getBean(XmlConstructorBean1.class);
        XmlConstructorBean2 xmlConstructorBean2 = applicationContext.getBean(XmlConstructorBean2.class);
        XmlConstructorBean3 xmlConstructorBean3 = applicationContext.getBean(XmlConstructorBean3.class);
        XmlConstructorBean4 xmlConstructorBean4 = applicationContext.getBean(XmlConstructorBean4.class);
        XmlConstructorBean5 xmlConstructorBean5 = applicationContext.getBean(XmlConstructorBean5.class);
        System.out.println("xmlConstructor1 : " + xmlConstructorBean1);
        System.out.println("xmlConstructor2 : " + xmlConstructorBean2);
        System.out.println("xmlConstructor3 : " + xmlConstructorBean3);
        System.out.println("xmlConstructor4 : " + xmlConstructorBean4);
        System.out.println("xmlConstructor5 : " + xmlConstructorBean5);
        Assert.assertEquals("xmlConstructorBean1", xmlConstructorBean1.getName());
        Assert.assertEquals("xmlConstructorBean2-v1", xmlConstructorBean2.getName());
        Assert.assertEquals("xmlConstructorBean3", xmlConstructorBean3.getName());
        Assert.assertEquals("xmlConstructorBean4", xmlConstructorBean4.getName());
        Assert.assertEquals("xmlConstructorBean5", xmlConstructorBean5.getName());
        XmlConstructorParentBean1 xmlConstructorParentBean1 = applicationContext.getBean(XmlConstructorParentBean1.class);
        XmlConstructorParentBean2 xmlConstructorParentBean2 = applicationContext.getBean(XmlConstructorParentBean2.class);
        XmlConstructorParentBean3 xmlConstructorParentBean3 = applicationContext.getBean(XmlConstructorParentBean3.class);
        XmlConstructorParentBean4 xmlConstructorParentBean4 = applicationContext.getBean(XmlConstructorParentBean4.class);
        XmlConstructorParentBean5 xmlConstructorParentBean5 = applicationContext.getBean(XmlConstructorParentBean5.class);
        XmlConstructorParentBeanMul1 xmlConstructorParentBeanMul1 = applicationContext.getBean(XmlConstructorParentBeanMul1.class);
        XmlConstructorParentBeanMul2 xmlConstructorParentBeanMul2 = applicationContext.getBean(XmlConstructorParentBeanMul2.class);
        Assert.assertEquals(xmlConstructorBean1, xmlConstructorParentBean1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBean2, xmlConstructorParentBean2.getXmlConstructorBean2());
        Assert.assertEquals(xmlConstructorBean3, xmlConstructorParentBean3.getXmlConstructorBean3());
        Assert.assertEquals(xmlConstructorBean4, xmlConstructorParentBean4.getXmlConstructorBean4());
        Assert.assertEquals(xmlConstructorBean5, xmlConstructorParentBean5.getXmlConstructorBean5());
        //check multiple
        Assert.assertNull(xmlConstructorParentBeanMul1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBean3, xmlConstructorParentBeanMul1.getXmlConstructorBean3());
        Assert.assertNull(xmlConstructorParentBeanMul1.getXmlConstructorBean4());
        Assert.assertNull(xmlConstructorParentBeanMul1.getXmlConstructorBean5());
        Assert.assertEquals(xmlConstructorBean1, xmlConstructorParentBeanMul2.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBean2, xmlConstructorParentBeanMul2.getXmlConstructorBean2());
        Assert.assertEquals(xmlConstructorBean3, xmlConstructorParentBeanMul2.getXmlConstructorBean3());

        // swap first time
        HotSwapper.swapClasses(XmlConstructorBean3.class, BakConstructorBean3.class.getName());
        HotSwapper.swapClasses(XmlConstructorBean4.class, BakConstructorBean4.class.getName());
        HotSwapper.swapClasses(XmlConstructorFactoryBean2.class, BakConstructorFactoryBean2.class.getName());
        Thread.sleep(8000);
        // check
        XmlConstructorBean1 xmlConstructorBeanNew1 = applicationContext.getBean(XmlConstructorBean1.class);
        XmlConstructorBean2 xmlConstructorBeanNew2 = applicationContext.getBean(XmlConstructorBean2.class);
        XmlConstructorBean3 xmlConstructorBeanNew3 = applicationContext.getBean(XmlConstructorBean3.class);
        XmlConstructorBean4 xmlConstructorBeanNew4 = applicationContext.getBean(XmlConstructorBean4.class);
        XmlConstructorBean5 xmlConstructorBeanNew5 = applicationContext.getBean(XmlConstructorBean5.class);
        System.out.println("xmlConstructor1 changed: " + xmlConstructorBeanNew1);
        System.out.println("xmlConstructor2 changed: " + xmlConstructorBeanNew2);
        System.out.println("xmlConstructor3 changed: " + xmlConstructorBeanNew3);
        System.out.println("xmlConstructor4 changed: " + xmlConstructorBeanNew4);
        System.out.println("xmlConstructor5 changed: " + xmlConstructorBeanNew5);
        Assert.assertEquals("xmlConstructorBean1", xmlConstructorBeanNew1.getName());
        Assert.assertEquals("ConstructorBean2-v2", xmlConstructorBeanNew2.getName());
        Assert.assertEquals("ConstructorBean3-v1", xmlConstructorBeanNew3.getName());
        Assert.assertEquals("ConstructorBean4-v1", xmlConstructorBeanNew4.getName());
        Assert.assertEquals("xmlConstructorBean5", xmlConstructorBeanNew5.getName());
        XmlConstructorParentBean1 xmlConstructorParentBeanNew1 = applicationContext.getBean(XmlConstructorParentBean1.class);
        XmlConstructorParentBean2 xmlConstructorParentBeanNew2 = applicationContext.getBean(XmlConstructorParentBean2.class);
        XmlConstructorParentBean3 xmlConstructorParentBeanNew3 = applicationContext.getBean(XmlConstructorParentBean3.class);
        XmlConstructorParentBean4 xmlConstructorParentBeanNew4 = applicationContext.getBean(XmlConstructorParentBean4.class);
        XmlConstructorParentBean5 xmlConstructorParentBeanNew5 = applicationContext.getBean(XmlConstructorParentBean5.class);
        XmlConstructorParentBeanMul1 xmlConstructorParentBeanMulNew1 = applicationContext.getBean(XmlConstructorParentBeanMul1.class);
        XmlConstructorParentBeanMul2 xmlConstructorParentBeanMulNew2 = applicationContext.getBean(XmlConstructorParentBeanMul2.class);
        Assert.assertEquals(xmlConstructorBeanNew1, xmlConstructorParentBeanNew1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBeanNew2, xmlConstructorParentBeanNew2.getXmlConstructorBean2());
        Assert.assertEquals(xmlConstructorBeanNew3, xmlConstructorParentBeanNew3.getXmlConstructorBean3());
        Assert.assertEquals(xmlConstructorBeanNew4, xmlConstructorParentBeanNew4.getXmlConstructorBean4());
        Assert.assertEquals(xmlConstructorBeanNew5, xmlConstructorParentBeanNew5.getXmlConstructorBean5());
        //check multiple
        Assert.assertNull(xmlConstructorParentBeanMulNew1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBeanNew3, xmlConstructorParentBeanMulNew1.getXmlConstructorBean3());
        Assert.assertNull(xmlConstructorParentBeanMulNew1.getXmlConstructorBean4());
        Assert.assertNull(xmlConstructorParentBeanMulNew1.getXmlConstructorBean5());
        Assert.assertEquals(xmlConstructorBeanNew1, xmlConstructorParentBeanMulNew2.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBeanNew2, xmlConstructorParentBeanMulNew2.getXmlConstructorBean2());
        Assert.assertEquals(xmlConstructorBeanNew3, xmlConstructorParentBeanMulNew2.getXmlConstructorBean3());

        Assert.assertEquals(xmlConstructorBeanNew1, xmlConstructorBean1);
        Assert.assertNotEquals(xmlConstructorBeanNew2, xmlConstructorBean2);
        Assert.assertNotEquals(xmlConstructorBeanNew3, xmlConstructorBean3);
        Assert.assertNotEquals(xmlConstructorBeanNew4, xmlConstructorBean4);
        Assert.assertEquals(xmlConstructorBeanNew5, xmlConstructorBean5);
        Assert.assertEquals(xmlConstructorParentBean1, xmlConstructorParentBeanNew1);
        Assert.assertNotEquals(xmlConstructorParentBean2, xmlConstructorParentBeanNew2);
        Assert.assertNotEquals(xmlConstructorParentBean3, xmlConstructorParentBeanNew3);
        Assert.assertNotEquals(xmlConstructorParentBean4, xmlConstructorParentBeanNew4);
        Assert.assertEquals(xmlConstructorParentBean5, xmlConstructorParentBeanNew5);
        Assert.assertNotEquals(xmlConstructorParentBeanMul1, xmlConstructorParentBeanMulNew1);
        Assert.assertNotEquals(xmlConstructorParentBeanMul2, xmlConstructorParentBeanMulNew2);

        // swap twice
        HotSwapper.swapClasses(XmlConstructorBean3.class, BakConstructorBean3V2.class.getName());
        HotSwapper.swapClasses(XmlConstructorBean4.class, BakConstructorBean4V2.class.getName());
        HotSwapper.swapClasses(XmlConstructorFactoryBean2.class, BakConstructorFactoryBean2V2.class.getName());
        HotSwapper.swapClasses(XmlConstructorParentBeanMul1.class, BakConstructorParentBeanMul1.class.getName());
        HotSwapper.swapClasses(XmlConstructorParentBeanMul2.class, BakConstructorParentBeanMul2.class.getName());
        Thread.sleep(8000);
        // check
        XmlConstructorBean1 xmlConstructorBeanV2_1 = applicationContext.getBean(XmlConstructorBean1.class);
        XmlConstructorBean2 xmlConstructorBeanV2_2 = applicationContext.getBean(XmlConstructorBean2.class);
        XmlConstructorBean3 xmlConstructorBeanV2_3 = applicationContext.getBean(XmlConstructorBean3.class);
        XmlConstructorBean4 xmlConstructorBeanV2_4 = applicationContext.getBean(XmlConstructorBean4.class);
        XmlConstructorBean5 xmlConstructorBeanV2_5 = applicationContext.getBean(XmlConstructorBean5.class);
        System.out.println("xmlConstructor1 changed v2: " + xmlConstructorBeanV2_1);
        System.out.println("xmlConstructor2 changed v2: " + xmlConstructorBeanV2_2);
        System.out.println("xmlConstructor3 changed v2: " + xmlConstructorBeanV2_3);
        System.out.println("xmlConstructor4 changed v2: " + xmlConstructorBeanV2_4);
        System.out.println("xmlConstructor5 changed v2: " + xmlConstructorBeanV2_5);
        Assert.assertEquals("xmlConstructorBean1", xmlConstructorBeanV2_1.getName());
        Assert.assertEquals("ConstructorBean2-v3", xmlConstructorBeanV2_2.getName());
        Assert.assertEquals("ConstructorBean3-v2", xmlConstructorBeanV2_3.getName());
        Assert.assertEquals("ConstructorBean4-v2", xmlConstructorBeanV2_4.getName());
        Assert.assertEquals("xmlConstructorBean5", xmlConstructorBeanV2_5.getName());
        XmlConstructorParentBean1 xmlConstructorParentBeanV2_1 = applicationContext.getBean(XmlConstructorParentBean1.class);
        XmlConstructorParentBeanMul1 xmlConstructorParentBeanV2_11 = applicationContext.getBean(XmlConstructorParentBeanMul1.class);
        XmlConstructorParentBean2 xmlConstructorParentBeanV2_2 = applicationContext.getBean(XmlConstructorParentBean2.class);
        XmlConstructorParentBean3 xmlConstructorParentBeanV2_3 = applicationContext.getBean(XmlConstructorParentBean3.class);
        XmlConstructorParentBean4 xmlConstructorParentBeanV2_4 = applicationContext.getBean(XmlConstructorParentBean4.class);
        XmlConstructorParentBean5 xmlConstructorParentBeanV2_5 = applicationContext.getBean(XmlConstructorParentBean5.class);
        XmlConstructorParentBeanMul1 xmlConstructorParentBeanMulV2_1 = applicationContext.getBean(XmlConstructorParentBeanMul1.class);
        XmlConstructorParentBeanMul2 xmlConstructorParentBeanMulV2_2 = applicationContext.getBean(XmlConstructorParentBeanMul2.class);
        Assert.assertEquals(xmlConstructorBeanV2_1, xmlConstructorParentBeanV2_1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBeanV2_2, xmlConstructorParentBeanV2_2.getXmlConstructorBean2());
        Assert.assertEquals(xmlConstructorBeanV2_3, xmlConstructorParentBeanV2_3.getXmlConstructorBean3());
        Assert.assertEquals(xmlConstructorBeanV2_4, xmlConstructorParentBeanV2_4.getXmlConstructorBean4());
        Assert.assertEquals(xmlConstructorBeanV2_5, xmlConstructorParentBeanV2_5.getXmlConstructorBean5());
        //check multiple
        Assert.assertEquals(xmlConstructorBeanV2_1, xmlConstructorParentBeanMulV2_1.getXmlConstructorBean1());
        Assert.assertEquals(xmlConstructorBeanV2_3, xmlConstructorParentBeanMulV2_1.getXmlConstructorBean3());
        Assert.assertEquals(xmlConstructorBeanV2_4, xmlConstructorParentBeanMulV2_1.getXmlConstructorBean4());
        Assert.assertEquals(xmlConstructorBeanV2_5, xmlConstructorParentBeanMulV2_1.getXmlConstructorBean5());
        Assert.assertEquals(xmlConstructorBeanV2_1, xmlConstructorParentBeanMulV2_2.getXmlConstructorBean1());
        try {
            xmlConstructorParentBeanMulV2_2.getXmlConstructorBean2();
            Assert.fail("remove the method, but still can invoke it");
        } catch (NoSuchMethodError e) {
            //ignore
        }
        try {
            xmlConstructorParentBeanMulV2_2.getXmlConstructorBean3();
            Assert.fail("remove the method, but still can invoke it");
        } catch (NoSuchMethodError e) {
            //ignore
        }

        Assert.assertEquals(xmlConstructorBeanNew1, xmlConstructorBeanV2_1);
        Assert.assertNotEquals(xmlConstructorBeanNew2, xmlConstructorBeanV2_2);
        Assert.assertNotEquals(xmlConstructorBeanNew3, xmlConstructorBeanV2_3);
        Assert.assertNotEquals(xmlConstructorBeanNew4, xmlConstructorBeanV2_4);
        Assert.assertEquals(xmlConstructorBeanNew5, xmlConstructorBeanV2_5);
        Assert.assertEquals(xmlConstructorParentBeanV2_1, xmlConstructorParentBeanNew1);
        Assert.assertNotEquals(xmlConstructorParentBeanV2_2, xmlConstructorParentBeanNew2);
        Assert.assertNotEquals(xmlConstructorParentBeanV2_3, xmlConstructorParentBeanNew3);
        Assert.assertNotEquals(xmlConstructorParentBeanV2_4, xmlConstructorParentBeanNew4);
        Assert.assertEquals(xmlConstructorParentBeanV2_5, xmlConstructorParentBeanNew5);
        Assert.assertNotEquals(xmlConstructorParentBeanMulV2_1, xmlConstructorParentBeanMulNew1);
        Assert.assertNotEquals(xmlConstructorParentBeanMulV2_2, xmlConstructorParentBeanMulNew2);
    }
}