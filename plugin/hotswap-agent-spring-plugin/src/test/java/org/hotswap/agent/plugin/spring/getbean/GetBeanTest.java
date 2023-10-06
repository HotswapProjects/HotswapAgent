package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Field;
import java.util.Map;


@RunWith(SpringRunner.class)
@ContextHierarchy({@ContextConfiguration(classes = GetBeanApplication.class)})
public class GetBeanTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    ApplicationContext applicationContext;

    //test DetachableBeanHolder.HA_PROXIES_CACHE
    @Test
    public void getBeanCacheTest() throws Exception {
        //values are instances of something like ServiceA$HOTSWAPAGENT_$$EnhancerBySpringCGLIB$$....
        Field haProxiesCache = DetachableBeanHolder.class
                .getDeclaredField("HA_PROXIES_CACHE");
        haProxiesCache.setAccessible(true);
        Map<String, Object> HA_PROXIES_CACHE = (Map<String, Object>) haProxiesCache
                .get(DetachableBeanHolder.class);

        DetachableBeanHolder.detachBeans();
        DetachableBeanHolder.detachBeans();

        //serviceA is cached
        String beanName = "serviceA";
        Object serviceA = applicationContext.getBean(beanName);
        Assert.assertSame(serviceA, HA_PROXIES_CACHE.get(beanName));

        //getBean("serviceA") won't generate proxy class and its bean again before swap (This is the bug fixed by this commit)
        Object serviceA1 = applicationContext.getBean(beanName);
        Assert.assertEquals(((ServiceA) serviceA).service(), "hello from serviceA and aspect");
        Assert.assertSame(serviceA, serviceA1);

        //swap
        swappingRule.swapClasses(ServiceA.class, ServiceB.class);

        //serviceA cache is removed
        Assert.assertFalse(HA_PROXIES_CACHE.containsKey(beanName));

        //getBean will create new proxy
        Object serviceAAfterSwap = applicationContext.getBean(beanName);
        Assert.assertEquals(((ServiceA) serviceAAfterSwap).service(), "hello from serviceB and aspect");

        //serviceA is cached again
        Assert.assertSame(serviceAAfterSwap, HA_PROXIES_CACHE.get(beanName));
    }


}
