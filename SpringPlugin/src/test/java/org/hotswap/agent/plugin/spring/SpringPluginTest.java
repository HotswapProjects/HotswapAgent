package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.testBeans.BeanPrototype;
import org.hotswap.agent.plugin.spring.testBeans.BeanRepository;
import org.hotswap.agent.plugin.spring.testBeans.BeanService;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanPrototype2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanRepository2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanService2;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jiri Bubnik
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
public class SpringPluginTest {

    @Autowired
    AutowireCapableBeanFactory factory;

    @Test
    public void basicTest() {
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());
    }

    @Test
    public void hotswapSeviceTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
        swapClasses(BeanService.class, BeanService2.class.getName());
        assertEquals("Hello from ChangedRepository Service2WithAspect", factory.getBean(BeanService.class).hello());

        // return configuration
        swapClasses(BeanService.class, BeanService.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
        swapClasses(BeanRepository.class, BeanRepository2.class.getName());
        assertEquals("Hello from ChangedRepository2 ServiceWithAspect", factory.getBean(BeanService.class).hello());

        // return configuration
        swapClasses(BeanRepository.class, BeanRepository.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
    }

    @Test
    public void hotswapPrototypeTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());

        // swap service this prototype is dependent to
        swapClasses(BeanService.class, BeanService2.class.getName());
        assertEquals("Hello from ChangedRepository Service2WithAspect Prototype", factory.getBean(BeanPrototype.class).hello());

        // swap autowired field
        swapClasses(BeanPrototype.class, BeanPrototype2.class.getName());
        assertEquals("Hello from Repository Prototype2", factory.getBean(BeanPrototype.class).hello());

        // return configuration
        swapClasses(BeanService.class, BeanService.class.getName());
        swapClasses(BeanPrototype.class, BeanPrototype.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());
    }

    private void swapClasses(Class original, String swap) throws Exception {
        ClassPathBeanDefinitionScannerAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ClassPathBeanDefinitionScannerAgent.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in Spring refresh?
        Thread.sleep(100);
    }
}
