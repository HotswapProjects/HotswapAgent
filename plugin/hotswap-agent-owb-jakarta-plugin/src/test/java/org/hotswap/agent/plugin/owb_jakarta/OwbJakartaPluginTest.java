/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.owb_jakarta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.owb_jakarta.command.BeanClassRefreshCommand;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.DependentHello1;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.HelloProducer1;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.HelloService;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.HelloServiceDependant;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.HelloServiceImpl1;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.InterceptedBean;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.ProxyHello1;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.ProxyHosting;
import org.hotswap.agent.plugin.owb_jakarta.testBeans.SessionBean1;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.DependentHello2;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.HelloProducer2;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.HelloProducer3;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.HelloServiceImpl2;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.InterceptedBean2;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.ProxyHello2;
import org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap.SessionBean2;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test OWB Plugin (test code could be synchronized with Weld test)
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Vladimir Dvorak
 */
public class OwbJakartaPluginTest extends HAAbstractUnitTest {

    public <T> T getBeanInstance(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        T result = beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
        return result;
    }

    public static <T> T getReference(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        if (bean != null) {
            return (T) beanManager.getReference(bean, beanClass, beanManager.createCreationalContext(bean));
        }
        return null;
    }

    @Before
    public void initContainer() {
        startContainer();
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
     * Add new method - invoke via reflection (not available at compilation time).
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
        HelloServiceImpl1 beanInstance = getBeanInstance(HelloServiceImpl1.class);
        assertEquals("HelloServiceImpl1.hello():HelloProducer1.hello()", beanInstance.hello());
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


    // Create new class and class file. rerun test only after clean
    @Test
    public void newBeanClassIsManagedBeanReRunTestOnlyAfterMvnClean() throws Exception {
        try {
            ClassFile.MAJOR_VERSION = ClassFile.JAVA_11; // hack, ASM9 does not support java17 (owb 2.0.26)
            OwbJakartaPlugin.isTestEnvironment = true;
            Class<?> clazz = getClass();
            String path = clazz.getResource(clazz.getSimpleName() + ".class")
                    .getPath().replace(clazz.getSimpleName() + ".class", "");
            //create new class and class file. rerun test only after clean
            Class newClass = HotSwapper.newClass("NewClass", OwbJakartaPlugin.archivePath, getClass().getClassLoader());
            Thread.sleep(1000); // wait redefine
            Object bean = getBeanInstance(newClass);
            assertNotNull(bean);
        } finally {
            OwbJakartaPlugin.isTestEnvironment = false;
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

    @Test
    @Ignore
    public void sessionBeanTest() throws Exception {
        SessionBean1 sessionBean = getBeanInstance(SessionBean1.class);
        assertEquals("SessionBean1.hello():ProxyHello1.hello()", sessionBean.hello());
        swapClasses(SessionBean1.class, SessionBean2.class.getName());

        assertEquals("SessionBean2.hello():ProxyHello2.hello():ProxyHello1.hello()", sessionBean.hello());

        // return configuration
        swapClasses(SessionBean1.class, SessionBean1.class.getName());
        assertEquals("SessionBean1.hello():ProxyHello1.hello()", sessionBean.hello());
    }

    @Test
    public void interceptedBeanTest() throws Exception {
        InterceptedBean interceptedBean = getReference(InterceptedBean.class);
        assertEquals("TestInterceptor:InterceptedBean.hello()", interceptedBean.hello());
        swapClasses(InterceptedBean.class, InterceptedBean2.class.getName());

        assertEquals("InterceptedBean2.hello():TestInterceptor:InterceptedBean2.hello2()", interceptedBean.hello());

        // return configuration
        swapClasses(InterceptedBean.class, InterceptedBean.class.getName());
        String s = interceptedBean.hello();
        System.out.println(s);
        assertEquals("TestInterceptor:InterceptedBean.hello()", s);
    }

    private void swapClasses(Class original, String swap) throws Exception {
        BeanClassRefreshCommand.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !BeanClassRefreshCommand.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in owb refresh?
        Thread.sleep(100);
    }
}
