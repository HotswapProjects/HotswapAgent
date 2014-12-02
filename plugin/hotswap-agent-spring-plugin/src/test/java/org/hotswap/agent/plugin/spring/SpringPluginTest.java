package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.testBeans.BeanPrototype;
import org.hotswap.agent.plugin.spring.testBeans.BeanRepository;
import org.hotswap.agent.plugin.spring.testBeans.BeanService;
import org.hotswap.agent.plugin.spring.testBeans.BeanServiceImpl;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanPrototype2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanRepository2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanServiceImpl2;
import org.hotswap.agent.util.ReflectionHelper;
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
 * Hotswap class files of spring beans.
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Jiri Bubnik
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
public class SpringPluginTest {

    @Autowired
    AutowireCapableBeanFactory factory;

    /**
     * Check correct setup.
     */
    @Test
    public void basicTest() {
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanService.class).hello());
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());
    }


    /**
     * Switch method implementation (using bean definition or interface).
     */
    @Test
    public void hotswapSeviceTest() throws Exception {
        BeanServiceImpl bean = factory.getBean(BeanServiceImpl.class);
		assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class.getName());
        assertEquals("Hello from ChangedRepository Service2WithAspect", bean.hello());
        // ensure that using interface is Ok as well
        assertEquals("Hello from ChangedRepository Service2WithAspect", factory.getBean(BeanService.class).hello());

        // return configuration
        swapClasses(BeanServiceImpl.class, BeanServiceImpl.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
    }


    /**
     * Add new method - invoke via reflection (not available at compilation time).
     */
    @Test
    public void hotswapSeviceAddMethodTest() throws Exception {
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class.getName());

        String helloNewMethodIfaceVal = (String) ReflectionHelper.invoke(factory.getBean(BeanService.class),
                BeanServiceImpl.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod Service2", helloNewMethodIfaceVal);

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(factory.getBean(BeanServiceImpl.class),
                BeanServiceImpl.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod Service2", helloNewMethodImplVal);

        // return configuration
        swapClasses(BeanServiceImpl.class, BeanServiceImpl.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanServiceImpl.class).hello());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {
        BeanServiceImpl bean = factory.getBean(BeanServiceImpl.class);
		assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
        swapClasses(BeanRepository.class, BeanRepository2.class.getName());
        assertEquals("Hello from ChangedRepository2 ServiceWithAspect", bean.hello());

        // return configuration
        swapClasses(BeanRepository.class, BeanRepository.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
    }

    @Test
    public void hotswapRepositoryNewMethodTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanServiceImpl.class).hello());
        swapClasses(BeanRepository.class, BeanRepository2.class.getName());

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(factory.getBean(BeanRepository.class),
                BeanRepository.class, "helloNewMethod", new Class[] {});
        assertEquals("Repository new method", helloNewMethodImplVal);

        // return configuration
        swapClasses(BeanRepository.class, BeanRepository.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect", factory.getBean(BeanServiceImpl.class).hello());
    }

    @Test
    public void hotswapPrototypeTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());

        // swap service this prototype is dependent to
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class.getName());
        assertEquals("Hello from ChangedRepository Service2WithAspect Prototype", factory.getBean(BeanPrototype.class).hello());

        // swap autowired field
        swapClasses(BeanPrototype.class, BeanPrototype2.class.getName());
        assertEquals("Hello from Repository Prototype2", factory.getBean(BeanPrototype.class).hello());

        // return configuration
        swapClasses(BeanServiceImpl.class, BeanServiceImpl.class.getName());
        swapClasses(BeanPrototype.class, BeanPrototype.class.getName());
        assertEquals("Hello from Repository ServiceWithAspect Prototype", factory.getBean(BeanPrototype.class).hello());
    }

    /**
     * Plugin is currently unable to reload prototype bean instance.
     */
    @Test
    public void hotswapPrototypeTestFailWhenHoldingInstance() throws Exception {
        BeanPrototype beanPrototypeInstance = factory.getBean(BeanPrototype.class);
        assertEquals("Hello from Repository ServiceWithAspect Prototype", beanPrototypeInstance.hello());

        // swap service this prototype is dependent to
        try {
            swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class.getName());
            assertEquals("Hello from ChangedRepository Service2WithAspect Prototype", beanPrototypeInstance.hello());
            throw new IllegalStateException("Reload prototype bean should not be correctly initialized.");
        } catch (NullPointerException e) {
            // BeanServiceImpl2 contains reference to different repository. Because existing reference
            // is not changed, this reference is null
        }

        // return configuration
        swapClasses(BeanServiceImpl.class, BeanServiceImpl.class.getName());
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
