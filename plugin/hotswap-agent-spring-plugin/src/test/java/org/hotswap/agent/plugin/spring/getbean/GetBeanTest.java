package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;


@RunWith(SpringRunner.class)
@ContextHierarchy({@ContextConfiguration(classes = GetBeanApplication.class)})
public class GetBeanTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Before
    public void setup() {
        BaseTestUtil.configMaxReloadTimes();
        swappingRule.setBeanFactory(beanFactory);
        BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).reset();
        System.out.println("SpringMvcTest.setup." + beanFactory);
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) beanFactory);
    }

    //test DetachableBeanHolder.HA_PROXIES_CACHE
    @Test
    public void getBeanCacheTest() throws Exception {
        //values are instances of something like ServiceA$HOTSWAPAGENT_$$EnhancerBySpringCGLIB$$....
        Field haProxiesCache = DetachableBeanHolder.class
                .getDeclaredField("HA_PROXIES_CACHE");
        haProxiesCache.setAccessible(true);
        Map<String, Object> HA_PROXIES_CACHE = (Map<String, Object>) haProxiesCache
                .get(DetachableBeanHolder.class);

        String beanName = "serviceA";
        DetachableBeanHolder.detachBean(beanName);
        DetachableBeanHolder.detachBean(beanName);

        //serviceA is cached
        Object serviceA = beanFactory.getBean(beanName);
        Assert.assertSame(serviceA, HA_PROXIES_CACHE.get(beanName));

        //getBean("serviceA") won't generate proxy class and its bean again before swap (This is the bug fixed by this commit)
        Object serviceA1 = beanFactory.getBean(beanName);
        Assert.assertEquals(((ServiceA) serviceA).service(), "hello from serviceA and aspect");
        Assert.assertSame(serviceA, serviceA1);

        //swap
        swappingRule.swapClasses(ServiceA.class, ServiceB.class, 1);

        //new proxy has been created by getBean
        Object serviceAAfterSwap = beanFactory.getBean(beanName);
        Assert.assertEquals(((ServiceA) serviceAAfterSwap).service(), "hello from serviceB and aspect");

        //serviceA is cached again
        Assert.assertSame(serviceAAfterSwap, HA_PROXIES_CACHE.get(beanName));
    }


}
