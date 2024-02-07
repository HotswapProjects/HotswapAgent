package org.hotswap.agent.plugin.spring.lifecycle;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.CommonTestBean;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitMethodBean1;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitMethodBean2;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitMethodBean3;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitMethodBean4;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitMethodBeanConfiguration;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitializingBean1;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitializingBean2;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitializingBean3;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.InitializingBean4;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.PostConstructorBean1;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.PostConstructorBean2;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.PostConstructorBean3;
import org.hotswap.agent.plugin.spring.lifecycle.beaninit.PostConstructorBean4;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {CommonTestBean.class, InitMethodBeanConfiguration.class,
    InitializingBean1.class, InitializingBean2.class, InitializingBean3.class, InitializingBean4.class,
    PostConstructorBean1.class, PostConstructorBean2.class, PostConstructorBean3.class, PostConstructorBean4.class})
public class BeanInitTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        System.out.println("BeanInitTest.before." + applicationContext.getBeanFactory());
        swappingRule.setBeanFactory(applicationContext.getBeanFactory());
        //        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory()).setPause(false);
    }

    @Test
    public void testInitMethod() throws Exception {
        InitializingBean1 initializingBean1 = getAndCheckBean(InitializingBean1.class);
        InitializingBean2 initializingBean2 = getAndCheckBean(InitializingBean2.class);
        InitializingBean3 initializingBean3 = getAndCheckBean(InitializingBean3.class);
        InitializingBean4 initializingBean4 = getAndCheckBean(InitializingBean4.class);
        InitMethodBean1 initMethodBean1 = getAndCheckBean(InitMethodBean1.class);
        InitMethodBean2 initMethodBean2 = getAndCheckBean(InitMethodBean2.class);
        InitMethodBean3 initMethodBean3 = getAndCheckBean(InitMethodBean3.class);
        InitMethodBean4 initMethodBean4 = getAndCheckBean(InitMethodBean4.class);
        PostConstructorBean1 postConstructorBean1 = getAndCheckBean(PostConstructorBean1.class);
        PostConstructorBean2 postConstructorBean2 = getAndCheckBean(PostConstructorBean2.class);
        PostConstructorBean3 postConstructorBean3 = getAndCheckBean(PostConstructorBean3.class);
        PostConstructorBean4 postConstructorBean4 = getAndCheckBean(PostConstructorBean4.class);
        Assert.assertTrue(initializingBean1.initialized.get());
        Assert.assertTrue(initializingBean2.initialized.get());
        Assert.assertTrue(initializingBean3.initialized.get());
        Assert.assertTrue(initializingBean4.initialized.get());
        Assert.assertTrue(initMethodBean1.initialized.get());
        Assert.assertTrue(initMethodBean2.initialized.get());
        Assert.assertTrue(initMethodBean3.initialized.get());
        Assert.assertTrue(initMethodBean4.initialized.get());
        Assert.assertTrue(postConstructorBean1.initialized.get());
        Assert.assertTrue(postConstructorBean2.initialized.get());
        Assert.assertTrue(postConstructorBean3.initialized.get());
        Assert.assertTrue(postConstructorBean4.initialized.get());
        //swap class
        swappingRule.swapClasses(CommonTestBean.class, CommonTestBean.class, 1);
        // get bean
        InitializingBean1 initializingBean1_1 = getAndCheckBean(InitializingBean1.class);
        InitializingBean2 initializingBean1_2 = getAndCheckBean(InitializingBean2.class);
        InitializingBean3 initializingBean1_3 = getAndCheckBean(InitializingBean3.class);
        InitializingBean4 initializingBean1_4 = getAndCheckBean(InitializingBean4.class);
        InitMethodBean1 initMethodBean1_1 = getAndCheckBean(InitMethodBean1.class);
        InitMethodBean2 initMethodBean1_2 = getAndCheckBean(InitMethodBean2.class);
        InitMethodBean3 initMethodBean1_3 = getAndCheckBean(InitMethodBean3.class);
        InitMethodBean4 initMethodBean1_4 = getAndCheckBean(InitMethodBean4.class);
        PostConstructorBean1 postConstructorBean1_1 = getAndCheckBean(PostConstructorBean1.class);
        PostConstructorBean2 postConstructorBean1_2 = getAndCheckBean(PostConstructorBean2.class);
        PostConstructorBean3 postConstructorBean1_3 = getAndCheckBean(PostConstructorBean3.class);
        PostConstructorBean4 postConstructorBean1_4 = getAndCheckBean(PostConstructorBean4.class);
        Assert.assertTrue(initializingBean1_1.initialized.get());
        Assert.assertFalse(initializingBean1_2.initialized.get());
        Assert.assertTrue(initializingBean1_3.initialized.get());
        Assert.assertTrue(initializingBean1_4.initialized.get());
        Assert.assertTrue(initMethodBean1_1.initialized.get());
        Assert.assertFalse(initMethodBean1_2.initialized.get());
        Assert.assertTrue(initMethodBean1_3.initialized.get());
        Assert.assertTrue(initMethodBean1_4.initialized.get());
        Assert.assertTrue(postConstructorBean1_1.initialized.get());
        Assert.assertFalse(postConstructorBean1_2.initialized.get());
        Assert.assertTrue(postConstructorBean1_3.initialized.get());
        Assert.assertTrue(postConstructorBean1_4.initialized.get());

        Assert.assertNotEquals(initializingBean1_1, initializingBean1);
        Assert.assertNotEquals(initializingBean1_2, initializingBean2);
        Assert.assertNotEquals(initializingBean1_3, initializingBean3);
        Assert.assertEquals(initializingBean1_4, initializingBean4);
        Assert.assertNotEquals(initMethodBean1_1, initMethodBean1);
        Assert.assertNotEquals(initMethodBean1_2, initMethodBean2);
        Assert.assertNotEquals(initMethodBean1_3, initMethodBean3);
        Assert.assertEquals(initMethodBean1_4, initMethodBean4);
        Assert.assertNotEquals(postConstructorBean1_1, postConstructorBean1);
        Assert.assertNotEquals(postConstructorBean1_2, postConstructorBean2);
        Assert.assertNotEquals(postConstructorBean1_3, postConstructorBean3);
        Assert.assertEquals(postConstructorBean1_4, postConstructorBean4);
    }

    private <T> T getAndCheckBean(Class<T> requiredType) throws BeansException {
        T t =  applicationContext.getBean(requiredType);
        Assert.assertNotNull(t);
        return t;
    }
}
