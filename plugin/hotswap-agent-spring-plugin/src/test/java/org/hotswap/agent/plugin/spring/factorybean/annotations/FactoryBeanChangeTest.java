package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.factorybean.bak.v1.BakAnnotationBean3;
import org.hotswap.agent.plugin.spring.factorybean.bak.v1.BakAnnotationBean4;
import org.hotswap.agent.plugin.spring.factorybean.bak.v1.BakAnnotationFactoryBean1;
import org.hotswap.agent.plugin.spring.factorybean.bak.v1.BakAnnotationFactoryBean2;
import org.hotswap.agent.plugin.spring.factorybean.bak.v2.V2BakAnnotationBean3;
import org.hotswap.agent.plugin.spring.factorybean.bak.v2.V2BakAnnotationBean4;
import org.hotswap.agent.plugin.spring.factorybean.bak.v2.V2BakAnnotationFactoryBean1;
import org.hotswap.agent.plugin.spring.factorybean.bak.v2.V2BakAnnotationFactoryBean2;
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
@ContextConfiguration(classes = {AnnoFactoryBeanConfiguration.class})
public class FactoryBeanChangeTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
    }

    @Test
    public void testFactoryBeanChanged() throws Exception {
        System.out.println("FactoryBeanChangeTest.testFactoryBeanChanged" + applicationContext.getBeanFactory());
        AnnotationBean1 annotationBean1 = applicationContext.getBean(AnnotationBean1.class);
        AnnotationBean2 annotationBean2 = applicationContext.getBean(AnnotationBean2.class);
        AnnotationBean3 annotationBean3 = applicationContext.getBean(AnnotationBean3.class);
        AnnotationBean4 annotationBean4 = applicationContext.getBean(AnnotationBean4.class);
        AnnotationBean5 annotationBean5 = applicationContext.getBean(AnnotationBean5.class);
        System.out.println("annotation1 : " + annotationBean1);
        System.out.println("annotation2 : " + annotationBean2);
        System.out.println("annotation3 : " + annotationBean3);
        System.out.println("annotation4 : " + annotationBean4);
        System.out.println("annotation5 : " + annotationBean5);
        Assert.assertEquals("AnnotationBean1", annotationBean1.getName());
        Assert.assertEquals("AnnotationBean2-v1", annotationBean2.getName());
        Assert.assertEquals("AnnotationBean3", annotationBean3.getName());
        Assert.assertEquals("AnnotationBean4", annotationBean4.getName());
        Assert.assertEquals("AnnotationBean5", annotationBean5.getName());
        AnnotationParentBean1 annotationParentBean1 = applicationContext.getBean(AnnotationParentBean1.class);
        AnnotationParentBean11 annotationParentBean11 = applicationContext.getBean(AnnotationParentBean11.class);
        AnnotationParentBean2 annotationParentBean2 = applicationContext.getBean(AnnotationParentBean2.class);
        AnnotationParentBean3 annotationParentBean3 = applicationContext.getBean(AnnotationParentBean3.class);
        AnnotationParentBean4 annotationParentBean4 = applicationContext.getBean(AnnotationParentBean4.class);
        AnnotationParentBean5 annotationParentBean5 = applicationContext.getBean(AnnotationParentBean5.class);
        Assert.assertEquals(annotationBean1, annotationParentBean1.getAnnotationBean1());
        Assert.assertEquals(annotationBean1, annotationParentBean11.getAnnotationBean1());
        Assert.assertEquals(annotationBean2, annotationParentBean2.getAnnotationBean2());
        Assert.assertEquals(annotationBean3, annotationParentBean3.getAnnotationBean3());
        Assert.assertEquals(annotationBean4, annotationParentBean4.getAnnotationBean4());
        Assert.assertEquals(annotationBean5, annotationParentBean5.getAnnotationBean5());
        // swap
        HotSwapper.swapClasses(AnnotationBean3.class, BakAnnotationBean3.class.getName());
        HotSwapper.swapClasses(AnnotationBean4.class, BakAnnotationBean4.class.getName());
        HotSwapper.swapClasses(AnnotationFactoryBean1.class, BakAnnotationFactoryBean1.class.getName());
        HotSwapper.swapClasses(AnnotationFactoryBean2.class, BakAnnotationFactoryBean2.class.getName());
        Thread.sleep(8000);
        // check
        AnnotationBean1 annotationBeanNew1 = applicationContext.getBean(AnnotationBean1.class);
        AnnotationBean2 annotationBeanNew2 = applicationContext.getBean(AnnotationBean2.class);
        AnnotationBean3 annotationBeanNew3 = applicationContext.getBean(AnnotationBean3.class);
        AnnotationBean4 annotationBeanNew4 = applicationContext.getBean(AnnotationBean4.class);
        AnnotationBean5 annotationBeanNew5 = applicationContext.getBean(AnnotationBean5.class);
        System.out.println("annotation1 changed: " + annotationBeanNew1);
        System.out.println("annotation2 changed: " + annotationBeanNew2);
        System.out.println("annotation3 changed: " + annotationBeanNew3);
        System.out.println("annotation4 changed: " + annotationBeanNew4);
        System.out.println("annotation5 changed: " + annotationBeanNew5);
        Assert.assertEquals("AnnotationBean1", annotationBeanNew1.getName());
        Assert.assertEquals("AnnotationBean2-v2", annotationBeanNew2.getName());
        Assert.assertEquals("AnnotationBean3-v1", annotationBeanNew3.getName());
        Assert.assertEquals("AnnotationBean4-v1", annotationBeanNew4.getName());
        Assert.assertEquals("AnnotationBean5", annotationBeanNew5.getName());
        AnnotationParentBean1 annotationParentBeanNew1 = applicationContext.getBean(AnnotationParentBean1.class);
        AnnotationParentBean11 annotationParentBeanNew11 = applicationContext.getBean(AnnotationParentBean11.class);
        AnnotationParentBean2 annotationParentBeanNew2 = applicationContext.getBean(AnnotationParentBean2.class);
        AnnotationParentBean3 annotationParentBeanNew3 = applicationContext.getBean(AnnotationParentBean3.class);
        AnnotationParentBean4 annotationParentBeanNew4 = applicationContext.getBean(AnnotationParentBean4.class);
        AnnotationParentBean5 annotationParentBeanNew5 = applicationContext.getBean(AnnotationParentBean5.class);
        Assert.assertEquals(annotationBeanNew1, annotationParentBeanNew1.getAnnotationBean1());
        Assert.assertEquals(annotationBeanNew1, annotationParentBeanNew11.getAnnotationBean1());
        Assert.assertEquals(annotationBeanNew2, annotationParentBeanNew2.getAnnotationBean2());
        Assert.assertEquals(annotationBeanNew3, annotationParentBeanNew3.getAnnotationBean3());
        Assert.assertEquals(annotationBeanNew4, annotationParentBeanNew4.getAnnotationBean4());
        Assert.assertEquals(annotationBeanNew5, annotationParentBeanNew5.getAnnotationBean5());

        Assert.assertNotEquals(annotationBeanNew1, annotationBean1);
        Assert.assertNotEquals(annotationBeanNew2, annotationBean2);
        Assert.assertNotEquals(annotationBeanNew3, annotationBean3);
        Assert.assertNotEquals(annotationBeanNew4, annotationBean4);
        Assert.assertEquals(annotationBeanNew5, annotationBean5);
        Assert.assertNotEquals(annotationParentBean1, annotationParentBeanNew1);
        Assert.assertNotEquals(annotationParentBean11, annotationParentBeanNew11);
        Assert.assertNotEquals(annotationParentBean2, annotationParentBeanNew2);
        Assert.assertNotEquals(annotationParentBean3, annotationParentBeanNew3);
        Assert.assertNotEquals(annotationParentBean4, annotationParentBeanNew4);
        Assert.assertEquals(annotationParentBean5, annotationParentBeanNew5);


        // swap v2
        HotSwapper.swapClasses(AnnotationBean3.class, V2BakAnnotationBean3.class.getName());
        HotSwapper.swapClasses(AnnotationBean4.class, V2BakAnnotationBean4.class.getName());
        HotSwapper.swapClasses(AnnotationFactoryBean1.class, V2BakAnnotationFactoryBean1.class.getName());
        HotSwapper.swapClasses(AnnotationFactoryBean2.class, V2BakAnnotationFactoryBean2.class.getName());
        Thread.sleep(8000);
        // check
        AnnotationBean1 annotationBeanV2_1 = applicationContext.getBean(AnnotationBean1.class);
        AnnotationBean2 annotationBeanV2_2 = applicationContext.getBean(AnnotationBean2.class);
        AnnotationBean3 annotationBeanV2_3 = applicationContext.getBean(AnnotationBean3.class);
        AnnotationBean4 annotationBeanV2_4 = applicationContext.getBean(AnnotationBean4.class);
        AnnotationBean5 annotationBeanV2_5 = applicationContext.getBean(AnnotationBean5.class);
        System.out.println("annotation1 changed v2: " + annotationBeanV2_1);
        System.out.println("annotation2 changed v2: " + annotationBeanV2_2);
        System.out.println("annotation3 changed v2: " + annotationBeanV2_3);
        System.out.println("annotation4 changed v2: " + annotationBeanV2_4);
        System.out.println("annotation5 changed v2: " + annotationBeanV2_5);
        Assert.assertEquals("AnnotationBean1", annotationBeanV2_1.getName());
        Assert.assertEquals("AnnotationBean2-v3", annotationBeanV2_2.getName());
        Assert.assertEquals("AnnotationBean3-v2", annotationBeanV2_3.getName());
        Assert.assertEquals("AnnotationBean4-v2", annotationBeanV2_4.getName());
        Assert.assertEquals("AnnotationBean5", annotationBeanV2_5.getName());
        AnnotationParentBean1 annotationParentBeanV2_1 = applicationContext.getBean(AnnotationParentBean1.class);
        AnnotationParentBean11 annotationParentBeanV2_11 = applicationContext.getBean(AnnotationParentBean11.class);
        AnnotationParentBean2 annotationParentBeanV2_2 = applicationContext.getBean(AnnotationParentBean2.class);
        AnnotationParentBean3 annotationParentBeanV2_3 = applicationContext.getBean(AnnotationParentBean3.class);
        AnnotationParentBean4 annotationParentBeanV2_4 = applicationContext.getBean(AnnotationParentBean4.class);
        AnnotationParentBean5 annotationParentBeanV2_5 = applicationContext.getBean(AnnotationParentBean5.class);
        Assert.assertEquals(annotationBeanV2_1, annotationParentBeanV2_1.getAnnotationBean1());
        Assert.assertEquals(annotationBeanV2_1, annotationParentBeanV2_11.getAnnotationBean1());
        Assert.assertEquals(annotationBeanV2_2, annotationParentBeanV2_2.getAnnotationBean2());
        Assert.assertEquals(annotationBeanV2_3, annotationParentBeanV2_3.getAnnotationBean3());
        Assert.assertEquals(annotationBeanV2_4, annotationParentBeanV2_4.getAnnotationBean4());
        Assert.assertEquals(annotationBeanV2_5, annotationParentBeanV2_5.getAnnotationBean5());

        Assert.assertNotEquals(annotationBeanNew1, annotationBeanV2_1);
        Assert.assertNotEquals(annotationBeanNew2, annotationBeanV2_2);
        Assert.assertNotEquals(annotationBeanNew3, annotationBeanV2_3);
        Assert.assertNotEquals(annotationBeanNew4, annotationBeanV2_4);
        Assert.assertEquals(annotationBeanNew5, annotationBeanV2_5);
        Assert.assertNotEquals(annotationParentBeanV2_1, annotationParentBeanNew1);
        Assert.assertNotEquals(annotationParentBeanV2_11, annotationParentBeanNew11);
        Assert.assertNotEquals(annotationParentBeanV2_2, annotationParentBeanNew2);
        Assert.assertNotEquals(annotationParentBeanV2_3, annotationParentBeanNew3);
        Assert.assertNotEquals(annotationParentBeanV2_4, annotationParentBeanNew4);
        Assert.assertEquals(annotationParentBeanV2_5, annotationParentBeanNew5);
    }
}
