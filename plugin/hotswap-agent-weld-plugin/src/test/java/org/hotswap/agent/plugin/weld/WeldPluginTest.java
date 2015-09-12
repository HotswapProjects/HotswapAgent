package org.hotswap.agent.plugin.weld;

import java.net.URL;
import java.util.Collection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent;
import org.hotswap.agent.plugin.weld.testBeans.ChangedHelloProducer;
import org.hotswap.agent.plugin.weld.testBeans.DependentHello;
import org.hotswap.agent.plugin.weld.testBeans.HelloProducer;
import org.hotswap.agent.plugin.weld.testBeans.HelloService;
import org.hotswap.agent.plugin.weld.testBeans.HelloServiceDependant;
import org.hotswap.agent.plugin.weld.testBeans.HelloServiceImpl;
import org.hotswap.agent.plugin.weld.testBeansHotswap.DependentHello2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloProducer2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloServiceImpl2;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Hotswap class files of weld beans.
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Jiri Bubnik / modified by Vladimir Dvorak
 */
@RunWith(WeldJUnit4Runner.class)
public class WeldPluginTest {

    public <T> T getBean(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        T result = beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
        return result;
    }

    /**
     * Check correct setup.
     */
    @Test
    public void basicTest() {
        assertEquals("Service:Hello", getBean(HelloService.class).hello());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());
    }

    /**
     * Switch method implementation (using bean definition or interface).
     */
    @Test
    public void hotswapServiceTest() throws Exception {

        HelloServiceImpl bean = getBean(HelloServiceImpl.class);
        assertEquals("Service:Hello", bean.hello());
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());

        assertEquals("null:ChangedHello", bean.hello());
        HelloServiceImpl.class.getMethod("initName", new Class[0]).invoke(bean, new Object[0]);
        assertEquals("Service2:ChangedHello", getBean(HelloServiceImpl.class).hello());
        // ensure that using interface is Ok as well
        assertEquals("Service2:ChangedHello", getBean(HelloService.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Service:Hello", bean.hello());

    }

    /**
     * Add new method - invoke via reflection (not available at compilation
     * time).
     */
    @Test
    public void hotswapSeviceAddMethodTest() throws Exception {
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());

        String helloNewMethodIfaceVal = (String) ReflectionHelper.invoke(getBean(HelloService.class),
                HelloServiceImpl.class, "helloNewMethod", new Class[]{});
        assertEquals("Hello from helloNewMethod", helloNewMethodIfaceVal);

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBean(HelloServiceImpl.class),
                HelloServiceImpl.class, "helloNewMethod", new Class[]{});
        assertEquals("Hello from helloNewMethod", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {

        HelloServiceDependant bean = getBean(HelloServiceDependant.class);
        assertEquals("Service:Hello", bean.hello());
        swapClasses(HelloProducer.class, ChangedHelloProducer.class.getName());
        assertEquals("Service:ChangedHello", bean.hello());
        swapClasses(HelloProducer.class, HelloProducer2.class.getName());
        try{
            assertEquals("Service:ChangedHello2", bean.hello());
        } catch (NullPointerException npe){
            System.out.println("Error: all linked beans are not updated injecton points");
            System.out.println("TODO: organize cache for dependant scope and reinitialize injection points");
            System.out.println("TODO: reinitialize singleton after swap dependant ????");
        }
        assertEquals("Service:ChangedHello2", getBean(HelloServiceDependant.class).hello());

        // return configuration
        swapClasses(HelloProducer.class, HelloProducer.class.getName());
        assertEquals("Service:Hello", bean.hello());

    }

    @Test
    public void hotswapRepositoryNewMethodTest() throws Exception {
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
        swapClasses(HelloProducer.class, HelloProducer2.class.getName());

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBean(HelloProducer.class),
                HelloProducer.class, "helloNewMethod", new Class[]{});
        assertEquals("Hello from helloNewMethod2", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloProducer.class, HelloProducer.class.getName());
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
    }

    @Test
    public void hotswapPrototypeTest() throws Exception {
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());

        // swap service this prototype is dependent to
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());

        assertEquals("Dependent:null:ChangedHello", getBean(DependentHello.class).hello());
        HelloServiceImpl.class.getMethod("initName", new Class[0]).invoke(getBean(HelloServiceImpl.class), new Object[0]);
        assertEquals("Dependent:Service2:ChangedHello", getBean(DependentHello.class).hello());

        // swap Inject field
        swapClasses(DependentHello.class, DependentHello2.class.getName());
        assertEquals("Dependant2:Hello", getBean(DependentHello.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        swapClasses(DependentHello.class, DependentHello.class.getName());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());
    }

    /**
     * Plugin is currently unable to reload prototype bean instance.
     */
    @Test
    public void hotswapPrototypeTestNotFailWhenHoldingInstanceBecauseSingletonInjectionPointWasReinitialize() throws Exception {

        DependentHello dependentBeanInstance = getBean(DependentHello.class);
        assertEquals("Dependent:Service:Hello", dependentBeanInstance.hello());

        // swap service this is dependent to
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());
        ReflectionHelper.invoke(getBean(HelloService.class),
                HelloServiceImpl.class, "initName", new Class[]{});
        assertEquals("Dependent:Service2:ChangedHello", dependentBeanInstance.hello());

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());

    }

    @Test
    public void newBeanClassIsManagedBeanReRunTestOnlyAfterMvnClean() throws Exception {
        WeldPlugin.IS_TEST_ENVIRONMENT = true;
        Collection<BeanDeploymentArchiveAgent> instances = BeanDeploymentArchiveAgent.getInstances();
        for (BeanDeploymentArchiveAgent instance : instances) {
            System.out.println(instance.getBdaId());
            //create new class and class file. rerun test only after clean
            Class newClass = HotSwapper.newClass("NewClass", instance.getBdaId(), getClass().getClassLoader());
            URL resource = newClass.getClassLoader().getResource("NewClass.class");
            System.out.println(resource);
            Thread.sleep(1000); // wait redefine
            Object bean = getBean(newClass);
            assertNotNull(bean);
            break;
        }
        WeldPlugin.IS_TEST_ENVIRONMENT = false;
    }

    private void swapClasses(Class original, String swap) throws Exception {
        BeanDeploymentArchiveAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !BeanDeploymentArchiveAgent.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in weld refresh?
        Thread.sleep(100);
    }
}
