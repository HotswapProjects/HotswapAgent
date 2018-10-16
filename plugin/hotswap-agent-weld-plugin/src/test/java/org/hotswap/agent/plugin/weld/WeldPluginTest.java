package org.hotswap.agent.plugin.weld;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Collection;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.weld.command.BeanClassRefreshAgent;
import org.hotswap.agent.plugin.weld.testBeans.DependentHello1;
import org.hotswap.agent.plugin.weld.testBeans.HelloProducer1;
import org.hotswap.agent.plugin.weld.testBeans.HelloService;
import org.hotswap.agent.plugin.weld.testBeans.HelloServiceDependant;
import org.hotswap.agent.plugin.weld.testBeans.HelloServiceImpl1;
import org.hotswap.agent.plugin.weld.testBeans.ProxyHello1;
import org.hotswap.agent.plugin.weld.testBeans.ProxyHosting;
import org.hotswap.agent.plugin.weld.testBeansHotswap.DependentHello2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloProducer2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloProducer3;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloServiceImpl2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.ProxyHello2;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test Weld Plugin (test code could be synchronized with OWB test)
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Vladimir Dvorak
 */
@RunWith(WeldJUnit4Runner.class)
public class WeldPluginTest {

    public <T> T getBeanInstance(Class<T> beanClass) {
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
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(HelloService.class).hello());
        assertEquals("DependentHello1.hello():HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(DependentHello1.class).hello());
    }

    /**
     * Switch method implementation (using bean definition or interface).
     */
    @Test
    public void hotswapServiceTest() throws Exception {

        HelloServiceImpl1 bean = getBeanInstance(HelloServiceImpl1.class);
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", bean.hello());

        swapClasses(HelloServiceImpl1.class, HelloServiceImpl2.class.getName());
        assertEquals("null:HelloProducer2.hello()", bean.hello());

        // Test set name="Service2" by reflection call
        HelloServiceImpl1.class.getMethod("initName", new Class[0]).invoke(bean, new Object[0]);
        assertEquals("HelloServiceImpl2.hello(initialized):HelloProducer2.hello()", getBeanInstance(HelloServiceImpl1.class).hello());
        // ensure that using interface is Ok as well
        assertEquals("HelloServiceImpl2.hello(initialized):HelloProducer2.hello()", getBeanInstance(HelloService.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl1.class.getName());
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", bean.hello());

    }

    /**
     * Add new method - invoke via reflection (not available at compilation
     * time).
     */
    @Test
    public void hotswapSeviceAddMethodTest() throws Exception {
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl2.class.getName());

        String helloNewMethodIfaceVal = (String) ReflectionHelper.invoke(getBeanInstance(HelloService.class),
                HelloServiceImpl1.class, "helloNewMethod", new Class[]{});
        assertEquals("HelloServiceImpl2.helloNewMethod()", helloNewMethodIfaceVal);

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBeanInstance(HelloServiceImpl1.class),
                HelloServiceImpl1.class, "helloNewMethod", new Class[]{});
        assertEquals("HelloServiceImpl2.helloNewMethod()", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl1.class.getName());
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(HelloServiceImpl1.class).hello());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {
        HelloServiceDependant bean = getBeanInstance(HelloServiceDependant.class);
        assertEquals("HelloServiceDependant.hello():HelloProducer1.hello()", bean.hello());
        swapClasses(HelloProducer1.class, HelloProducer2.class.getName());
        assertEquals("HelloServiceDependant.hello():HelloProducer2.hello()", bean.hello());
        swapClasses(HelloProducer1.class, HelloProducer3.class.getName());
        try{
            assertEquals("HelloProducer3.hello():HelloProducer2.hello()", bean.hello());
        } catch (NullPointerException npe){
            System.out.println("INFO: dependant beans are not reinjected now.");
        }
        assertEquals("HelloServiceDependant.hello():HelloProducer3.hello():HelloProducer2.hello()",
                getBeanInstance(HelloServiceDependant.class).hello());

        // return configuration
        swapClasses(HelloProducer1.class, HelloProducer1.class.getName());
        assertEquals("HelloServiceDependant.hello():HelloProducer1.hello()", bean.hello());
    }

    @Test
    public void hotswapRepositoryNewMethodTest() throws Exception {
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(HelloServiceImpl1.class).hello());
        swapClasses(HelloProducer1.class, HelloProducer3.class.getName());
        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBeanInstance(HelloProducer1.class),
                HelloProducer1.class, "helloNewMethod", new Class[]{});
        assertEquals("HelloProducer3.helloNewMethod()", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloProducer1.class, HelloProducer1.class.getName());
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(HelloServiceImpl1.class).hello());
    }

    @Test
    public void hotswapPrototypeTest() throws Exception {
        assertEquals("DependentHello1.hello():HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(DependentHello1.class).hello());

        // swap service this prototype is dependent to
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl2.class.getName());

        assertEquals("DependentHello1.hello():null:HelloProducer2.hello()", getBeanInstance(DependentHello1.class).hello());
        HelloServiceImpl1.class.getMethod("initName", new Class[0]).invoke(getBeanInstance(HelloServiceImpl1.class), new Object[0]);
        assertEquals("DependentHello1.hello():HelloServiceImpl2.hello(initialized):HelloProducer2.hello()", getBeanInstance(DependentHello1.class).hello());

        // swap Inject field
        swapClasses(DependentHello1.class, DependentHello2.class.getName());
        assertEquals("DependentHello2.hello():HelloProducer1.hello()", getBeanInstance(DependentHello1.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl1.class.getName());
        swapClasses(DependentHello1.class, DependentHello1.class.getName());
        assertEquals("DependentHello1.hello():HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(DependentHello1.class).hello());
    }

    /**
     * Plugin is currently unable to reload prototype bean instance.
     */
    @Test
    public void hotswapPrototypeTestNotFailWhenHoldingInstanceBecauseSingletonInjectionPointWasReinitialize() throws Exception {
        DependentHello1 dependentBeanInstance = getBeanInstance(DependentHello1.class);
        assertEquals("DependentHello1.hello():HelloServiceImpl1.hello():HelloProducer1.hello()", dependentBeanInstance.hello());

        // swap service this is dependent to
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl2.class.getName());
        ReflectionHelper.invoke(getBeanInstance(HelloService.class),
                HelloServiceImpl1.class, "initName", new Class[]{});
        assertEquals("DependentHello1.hello():HelloServiceImpl2.hello(initialized):HelloProducer2.hello()", dependentBeanInstance.hello());

        // return configuration
        swapClasses(HelloServiceImpl1.class, HelloServiceImpl1.class.getName());
        assertEquals("DependentHello1.hello():HelloServiceImpl1.hello():HelloProducer1.hello()", getBeanInstance(DependentHello1.class).hello());
    }

    @Test
    public void newBeanClassIsManagedBeanReRunTestOnlyAfterMvnClean() throws Exception {
        try {
            WeldPlugin.isTestEnvironment = true;
            Collection<BeanClassRefreshAgent> instances = BeanClassRefreshAgent.getInstances();
            for (BeanClassRefreshAgent instance : instances) {
                //create new class and class file. rerun test only after clean
                Class newClass = HotSwapper.newClass("NewClass", instance.getBdaId(), getClass().getClassLoader());
                URL resource = newClass.getClassLoader().getResource("NewClass.class");
                Thread.sleep(1000); // wait redefine
                Object bean = getBeanInstance(newClass);
                assertNotNull(bean);
                break;
            }
        } finally {
            WeldPlugin.isTestEnvironment = false;
        }
    }

    @Test
    public void proxyTest() throws Exception {
        ProxyHosting proxyHosting = getBeanInstance(ProxyHosting.class);
        assertEquals("ProxyHello1.hello()", proxyHosting.hello());
        swapClasses(ProxyHello1.class, ProxyHello2.class.getName());

        assertEquals("ProxyHello2.hello()", proxyHosting.hello());
        Object proxy = proxyHosting.proxy;
        String hello2 = (String) ReflectionHelper.invoke(proxy, ProxyHello1.class, "hello2", new Class[]{}, null);
        assertEquals("ProxyHello2.hello2()", hello2);

        // return configuration
        swapClasses(ProxyHello1.class, ProxyHello1.class.getName());
        assertEquals("ProxyHello1.hello()", proxyHosting.hello());
    }

    private void swapClasses(Class original, String swap) throws Exception {
        BeanClassRefreshAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !BeanClassRefreshAgent.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in weld refresh?
        Thread.sleep(100);
    }
}
