/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JDK dynamic proxy beans are not wrapped, so code of other libraries can cast their invocation handler (#327). After a
 * reload, a kept proxy calls the new bean, on every Java version (#737).
 */
public class JdkProxyBeanTest {

    public interface Greeter {
        String greet();
    }

    public static class GreeterImpl implements Greeter {
        static final AtomicInteger INSTANCES = new AtomicInteger();
        private final int instance = INSTANCES.incrementAndGet();

        @Override
        public String greet() {
            return "instance " + instance;
        }
    }

    /**
     * Handler of a JDK proxy of another library, which casts the handler of its proxies, like CXF ClientProxy.
     */
    public static class ForeignHandler implements InvocationHandler {
        private final Greeter target;

        ForeignHandler(Greeter target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                // equals, hashCode and toString of the proxy itself
                return method.getName().equals("equals") ? proxy == args[0]
                        : method.getName().equals("hashCode") ? System.identityHashCode(proxy) : "foreign proxy";
            }
            return "foreign " + method.invoke(target, args);
        }
    }

    @Configuration
    public static class JdkProxyConfiguration {
        @Bean
        public GreeterImpl greeterImpl() {
            return new GreeterImpl();
        }

        // a JDK dynamic proxy, like Spring AOP creates for a bean with an interface
        @Bean(name = {"springAopGreeter", "springAopGreeterAlias"})
        public ProxyFactoryBean springAopGreeter(GreeterImpl greeterImpl) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(greeterImpl);
            factory.setInterfaces(Greeter.class);
            return factory;
        }

        @Bean
        public Greeter foreignGreeter(GreeterImpl greeterImpl) {
            return foreignProxy(greeterImpl);
        }

        @Bean
        @Scope("prototype")
        public Greeter prototypeGreeter() {
            return foreignProxy(new GreeterImpl());
        }

        @Bean
        @Scope("prototype")
        public Greeter dependentPrototypeGreeter(GreeterImpl greeterImpl) {
            return foreignProxy(greeterImpl);
        }

        // a new Spring AOP proxy for each getBean(). Spring also creates the factory to find the type of the bean.
        @Bean
        @Scope("prototype")
        public ProxyFactoryBean prototypeSpringAopGreeter(GreeterImpl greeterImpl) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(greeterImpl);
            factory.setInterfaces(Greeter.class);
            return factory;
        }

        @Bean
        @Lazy
        public Greeter lazyGreeter() {
            return foreignProxy(new GreeterImpl());
        }

        // created with getBean(name, owner)
        @Bean
        @Scope("prototype")
        public Greeter ownedGreeter(Owner owner) {
            return foreignProxy(new GreeterImpl());
        }

        @Bean
        @Scope(CONVERSATION)
        public Greeter conversationGreeter() {
            return foreignProxy(new GreeterImpl());
        }

        static Greeter foreignProxy(Greeter target) {
            return (Greeter) Proxy.newProxyInstance(Greeter.class.getClassLoader(), new Class<?>[]{Greeter.class},
                    new ForeignHandler(target));
        }
    }

    /**
     * Owner of a bean, which is given to the bean factory to create the bean, and keeps the bean.
     */
    public static class Owner {
        Greeter greeter;
    }

    static final String CONVERSATION = "conversation";

    /**
     * A scope like the one of a session: each conversation has its own beans.
     */
    public static class ConversationScope implements org.springframework.beans.factory.config.Scope {
        String current = "A";
        private final Map<String, Object> beans = new HashMap<>();

        @Override
        public Object get(String name, ObjectFactory<?> objectFactory) {
            return beans.computeIfAbsent(current + ":" + name, k -> objectFactory.getObject());
        }

        @Override
        public Object remove(String name) {
            return beans.remove(current + ":" + name);
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback) {
        }

        @Override
        public Object resolveContextualObject(String key) {
            return null;
        }

        @Override
        public String getConversationId() {
            return current;
        }
    }

    /**
     * Counts the beans which a refreshed proxy gets from the bean factory.
     */
    public static class CountingBeanFactory {
        private final DefaultListableBeanFactory beanFactory;
        int calls;

        CountingBeanFactory(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        public Object getBean(String name) {
            calls++;
            return beanFactory.getBean(name);
        }
    }

    @Configuration
    public static class ChildConfiguration {
        @Bean
        @Scope("prototype")
        public Greeter childPrototypeGreeter() {
            return JdkProxyConfiguration.foreignProxy(new GreeterImpl());
        }
    }

    @Configuration
    public static class PrimaryConfiguration {
        @Bean
        @Primary
        public Greeter primaryGreeter() {
            return JdkProxyConfiguration.foreignProxy(new GreeterImpl());
        }

        @Bean
        public Greeter otherGreeter() {
            return JdkProxyConfiguration.foreignProxy(new GreeterImpl());
        }
    }

    /**
     * Handler of a JDK proxy that can be serialized, e.g. of a bean kept in an HTTP session.
     */
    public static class SerializableHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return "serializable";
        }
    }

    private AnnotationConfigApplicationContext context;
    private DefaultListableBeanFactory beanFactory;
    private final ConversationScope conversationScope = new ConversationScope();

    @Before
    public void before() {
        context = new AnnotationConfigApplicationContext(JdkProxyConfiguration.class);
        beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        // its beans are created when they are used
        beanFactory.registerScope(CONVERSATION, conversationScope);
    }

    /**
     * Unregister the bean factory from the agent before closing, otherwise later reloads in other tests
     * would try to reload this closed context and abort.
     */
    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory(beanFactory);
        context.close();
    }

    @Test
    public void jdkProxiesAreNotWrapped() {
        Object springAopGreeter = context.getBean("springAopGreeter");
        Object foreignGreeter = context.getBean("foreignGreeter");

        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(springAopGreeter).getClass().getName());
        assertTrue(Proxy.getInvocationHandler(foreignGreeter) instanceof ForeignHandler);
        assertSame(springAopGreeter, context.getBean("springAopGreeter"));
        assertSame(foreignGreeter, context.getBean("foreignGreeter"));
    }

    @Test
    public void keptSpringAopJdkProxyCallsNewSingleton() {
        Greeter greeter = (Greeter) context.getBean("springAopGreeter");
        assertEquals("instance " + GreeterImpl.INSTANCES.get(), greeter.greet());

        reload(true);

        // the singleton exists, the proxy got the handler of the new proxy at once
        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(greeter).getClass().getName());
        assertEquals("instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        assertTrue(greeter.equals(context.getBean("springAopGreeter")));
    }

    @Test
    public void keptForeignJdkProxyCallsNewSingleton() {
        Greeter greeter = (Greeter) context.getBean("foreignGreeter");
        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());

        reload(true);

        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
    }

    @Test
    public void keptJdkProxyGetsSingletonNotCreatedYetWhenCalled() {
        Greeter greeter = (Greeter) context.getBean("springAopGreeter");
        int instances = GreeterImpl.INSTANCES.get();

        reload(false);

        // the bean is created when the proxy is called, not by the reload
        assertEquals(instances, GreeterImpl.INSTANCES.get());
        assertEquals("instance " + (instances + 1), greeter.greet());
        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(greeter).getClass().getName());
        assertTrue(greeter.equals(context.getBean("springAopGreeter")));
    }

    @Test
    public void keptPrototypeJdkProxyGetsNewPrototypeWhenCalled() {
        Greeter greeter = (Greeter) context.getBean("prototypeGreeter");
        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        int instances = GreeterImpl.INSTANCES.get();

        reloadPrototype();

        assertEquals(instances, GreeterImpl.INSTANCES.get());
        assertEquals("foreign instance " + (instances + 1), greeter.greet());
        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
    }

    @Test
    public void keptPrototypeJdkProxyKeepsItsBeanAndHandlerIfTheBeanWasNotReloaded() {
        Greeter greeter = (Greeter) context.getBean("prototypeGreeter");
        InvocationHandler handler = Proxy.getInvocationHandler(greeter);
        String greeting = greeter.greet();

        // another bean has changed
        reload(true);

        // code of other libraries can still cast the handler, without a call of the proxy before
        assertSame(handler, Proxy.getInvocationHandler(greeter));
        assertEquals(greeting, greeter.greet());
    }

    @Test
    public void keptPrototypeJdkProxyGetsNewPrototypeIfItsDependencyWasReloaded() {
        Greeter greeter = (Greeter) context.getBean("dependentPrototypeGreeter");
        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());

        // destroys the singleton greeterImpl
        reload(true);

        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
    }

    @Test
    public void keptJdkProxyOfAliasCallsNewSingleton() {
        Greeter greeter = (Greeter) context.getBean("springAopGreeterAlias");

        reload(true);

        // the alias is resolved, the singleton is found and the proxy got the handler of the new proxy at once
        assertEquals("org.springframework.aop.framework.JdkDynamicAopProxy",
                Proxy.getInvocationHandler(greeter).getClass().getName());
        assertEquals("instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
    }

    @Test
    public void proxyCallingBeanOfOtherClassIsEqualOnlyToItself() {
        Greeter greeter = (Greeter) context.getBean("foreignGreeter");
        GreeterImpl bean = new GreeterImpl();
        // set by a reload if the new bean is not a proxy of the same class
        JdkProxyRefresher.setHandler(greeter, new RefreshingInvocationHandler(beanFactory,
                new Class<?>[]{String.class}, new Object[]{"foreignGreeter"}, null, bean));

        assertEquals(bean.greet(), greeter.greet());
        assertTrue(greeter.equals(greeter));
        assertFalse(greeter.equals(bean));
        assertEquals(System.identityHashCode(greeter), greeter.hashCode());
    }

    @Test
    public void basePackagePrefixIsCheckedAgainstTheInterfaces() {
        String[] basePackagePrefixes = SpringPlugin.basePackagePrefixes;
        try {
            SpringPlugin.basePackagePrefixes = new String[]{"com.example"};
            assertFalse(JdkProxyRefresher.isTracked(context.getBean("prototypeGreeter")));

            SpringPlugin.basePackagePrefixes = new String[]{Greeter.class.getPackage().getName()};
            assertTrue(JdkProxyRefresher.isTracked(context.getBean("prototypeGreeter")));
        } finally {
            SpringPlugin.basePackagePrefixes = basePackagePrefixes;
        }
    }

    @Test
    public void refreshedProxyThatIsStillTheBeanGetsItsHandlerBack() {
        Greeter greeter = (Greeter) context.getBean("foreignGreeter");
        InvocationHandler handler = Proxy.getInvocationHandler(greeter);
        String greeting = greeter.greet();
        // set by a reload to a proxy of a bean whose name is not known, e.g. kept from getBean(Class)
        JdkProxyRefresher.setHandler(greeter, new RefreshingInvocationHandler(beanFactory,
                new Class<?>[]{String.class}, new Object[]{"foreignGreeter"}, handler, null));

        assertEquals(greeting, greeter.greet());
        assertSame(handler, Proxy.getInvocationHandler(greeter));
    }

    @Test
    public void keptJdkProxyOfRemovedBeanCallsNewSingletonWhenTheBeanIsDefinedAgain() {
        Greeter greeter = (Greeter) context.getBean("foreignGreeter");
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("foreignGreeter");

        beanFactory.removeBeanDefinition("foreignGreeter");
        reload(true);
        assertTrue(JdkProxyRefresher.isTracked(greeter));

        beanFactory.registerBeanDefinition("foreignGreeter", beanDefinition);
        reload(true);

        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
        assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
    }

    @Test
    public void keptJdkProxyOfParentBeanReturnedByChildIsRefreshedByReloadOfParent() {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            child.setParent(context);
            child.register(ChildConfiguration.class);
            child.refresh();
            // Spring gets the bean of the parent without its getBean method, the proxy is known to the child only
            Greeter greeter = (Greeter) child.getBean("prototypeGreeter");
            Greeter childGreeter = (Greeter) child.getBean("childPrototypeGreeter");
            String childGreeting = childGreeter.greet();
            int instances = GreeterImpl.INSTANCES.get();

            reloadPrototype();

            assertEquals("foreign instance " + (instances + 1), greeter.greet());
            // the bean of the child itself is not reloaded
            assertTrue(Proxy.getInvocationHandler(childGreeter) instanceof ForeignHandler);
            assertEquals(childGreeting, childGreeter.greet());
            assertEquals(instances + 1, GreeterImpl.INSTANCES.get());
        } finally {
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) child.getBeanFactory());
            child.close();
        }
    }

    @Test
    public void callsOfRefreshedPrototypeProxyUseTheSameBean() throws Throwable {
        Greeter greeter = (Greeter) context.getBean("prototypeGreeter");
        reloadPrototype();
        InvocationHandler refreshing = Proxy.getInvocationHandler(greeter);
        assertTrue(refreshing instanceof RefreshingInvocationHandler);
        String greeting = greeter.greet();
        int instances = GreeterImpl.INSTANCES.get();

        // a thread that called the proxy before the first call set its handler
        Object lateGreeting = refreshing.invoke(greeter, Greeter.class.getMethod("greet"), null);

        assertEquals(greeting, lateGreeting);
        assertEquals(instances, GreeterImpl.INSTANCES.get());
        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
    }

    @Test
    public void objectMethodsOfRefreshedProxyDoNotFailWithoutTheBean() {
        Greeter greeter = (Greeter) context.getBean("prototypeGreeter");
        // e.g. a bean of a request, used outside of a request
        JdkProxyRefresher.setHandler(greeter, new RefreshingInvocationHandler(beanFactory,
                new Class<?>[]{String.class}, new Object[]{"missingGreeter"}, null, null));

        assertEquals(System.identityHashCode(greeter), greeter.hashCode());
        assertTrue(greeter.equals(greeter));
        assertTrue(greeter.toString().contains("@"));
        try {
            greeter.greet();
            fail("the bean does not exist");
        } catch (NoSuchBeanDefinitionException expected) {
            // the call of the bean itself still fails
        }
    }

    @Test
    public void refreshedProxyIsSerializedWithItsPreviousHandler() throws Exception {
        Greeter greeter = (Greeter) Proxy.newProxyInstance(Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class}, new SerializableHandler());
        JdkProxyRefresher.setHandler(greeter, new RefreshingInvocationHandler(beanFactory,
                new Class<?>[]{String.class}, new Object[]{"prototypeGreeter"}, Proxy.getInvocationHandler(greeter), null));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(greeter);
        }
        Object copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveProxyClass(String[] interfaces) throws ClassNotFoundException {
                Class<?>[] classes = new Class<?>[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    classes[i] = Class.forName(interfaces[i], false, Greeter.class.getClassLoader());
                }
                return Proxy.getProxyClass(Greeter.class.getClassLoader(), classes);
            }

            @Override
            protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws ClassNotFoundException {
                return Class.forName(desc.getName(), false, Greeter.class.getClassLoader());
            }
        }) {
            copy = in.readObject();
        }

        assertTrue(Proxy.getInvocationHandler(copy) instanceof SerializableHandler);
    }

    @Test
    public void prototypeJdkProxyReturnedAfterItsBeanWasDestroyedIsNotRefreshed() {
        Greeter kept = (Greeter) context.getBean("prototypeGreeter");
        beanFactory.destroySingleton("prototypeGreeter");
        // returned by the reload itself, e.g. to a singleton it creates again
        Greeter created = (Greeter) context.getBean("prototypeGreeter");
        String greeting = created.greet();

        JdkProxyRefresher.refresh(beanFactory);

        assertTrue(Proxy.getInvocationHandler(kept) instanceof RefreshingInvocationHandler);
        assertTrue(Proxy.getInvocationHandler(created) instanceof ForeignHandler);
        assertEquals(greeting, created.greet());
    }

    @Test
    public void destroyedBeansAreForgottenByTheRefresh() {
        Greeter greeter = (Greeter) context.getBean("prototypeGreeter");
        reloadPrototype();
        String greeting = greeter.greet();

        // the next reload, of another bean
        reload(true);

        assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
        assertEquals(greeting, greeter.greet());
    }

    @Test
    public void keptJdkProxyOfParentSingletonReturnedByChildKeepsItsHandler() {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            child.setParent(context);
            child.refresh();
            // created by the parent, but returned by the child only
            Greeter greeter = (Greeter) child.getBean("lazyGreeter");
            InvocationHandler handler = Proxy.getInvocationHandler(greeter);
            String greeting = greeter.greet();

            // a reload of the parent and of the child that does not change the bean
            reload(true);
            JdkProxyRefresher.refresh((DefaultListableBeanFactory) child.getBeanFactory());

            assertSame(handler, Proxy.getInvocationHandler(greeter));
            assertEquals(greeting, greeter.greet());

            // the bean is changed: the kept proxy gets the handler of the new singleton at once
            beanFactory.destroySingleton("lazyGreeter");
            context.getBean("lazyGreeter");
            JdkProxyRefresher.refresh(beanFactory);

            assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
            assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        } finally {
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) child.getBeanFactory());
            child.close();
        }
    }

    @Test
    public void keptJdkProxyOfParentSingletonIsRefreshedAfterTheChildThatReturnedItIsRemoved() {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            child.setParent(context);
            child.refresh();
            // created by the parent, but returned by the child only
            Greeter greeter = (Greeter) child.getBean("lazyGreeter");
            // the child is forgotten by the agent, e.g. by the MyBatis plugin
            SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) child.getBeanFactory());
            child.close();

            beanFactory.destroySingleton("lazyGreeter");
            context.getBean("lazyGreeter");
            JdkProxyRefresher.refresh(beanFactory);

            assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
            assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        } finally {
            child.close();
        }
    }

    @Test
    public void keptJdkProxyOfPrimaryBeanReturnedByItsTypeKeepsItsHandlerIfTheBeanWasNotReloaded() {
        AnnotationConfigApplicationContext primary = new AnnotationConfigApplicationContext(PrimaryConfiguration.class);
        DefaultListableBeanFactory primaryFactory = (DefaultListableBeanFactory) primary.getBeanFactory();
        try {
            // getBean(Class) gets the bean by its name, the proxy is tracked with the name of the primary bean
            Greeter greeter = primary.getBean(Greeter.class);
            InvocationHandler handler = Proxy.getInvocationHandler(greeter);
            assertTrue(JdkProxyRefresher.isTracked(greeter));

            JdkProxyRefresher.refresh(primaryFactory);

            assertSame(handler, Proxy.getInvocationHandler(greeter));

            // the bean is changed: the kept proxy gets the handler of the new singleton at once
            primaryFactory.destroySingleton("primaryGreeter");
            primary.getBean("primaryGreeter");
            JdkProxyRefresher.refresh(primaryFactory);

            assertNotSame(handler, Proxy.getInvocationHandler(greeter));
            assertTrue(Proxy.getInvocationHandler(greeter) instanceof ForeignHandler);
            assertEquals("foreign instance " + GreeterImpl.INSTANCES.get(), greeter.greet());
        } finally {
            SpringChangedAgent.destroyBeanFactory(primaryFactory);
            primary.close();
        }
    }

    @Test
    public void keptJdkProxyOfBeanCreatedWithArgumentsGetsNewPrototypeWhenCalled() {
        Owner owner = new Owner();
        owner.greeter = (Greeter) context.getBean("ownedGreeter", owner);
        owner.greeter.greet();
        int instances = GreeterImpl.INSTANCES.get();

        beanFactory.destroySingleton("ownedGreeter");
        JdkProxyRefresher.refresh(beanFactory);

        assertEquals("foreign instance " + (instances + 1), owner.greeter.greet());
    }

    @Test
    public void trackedJdkProxyReferencedByItsArgumentsIsCollected() throws Exception {
        Owner owner = new Owner();
        owner.greeter = (Greeter) context.getBean("ownedGreeter", owner);
        WeakReference<Object> proxy = new WeakReference<Object>(owner.greeter);

        owner = null;
        for (int i = 0; i < 100 && proxy.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }

        assertEquals(null, proxy.get());
    }

    @Test
    public void keptJdkProxyOfScopedBeanStaysTheBeanOfItsScope() {
        Greeter greeterA = (Greeter) context.getBean("conversationGreeter");
        InvocationHandler handlerA = Proxy.getInvocationHandler(greeterA);
        String greetingA = greeterA.greet();
        conversationScope.current = "B";
        Greeter greeterB = (Greeter) context.getBean("conversationGreeter");
        String greetingB = greeterB.greet();

        beanFactory.destroySingleton("conversationGreeter");
        JdkProxyRefresher.refresh(beanFactory);

        // called in the other conversation: it must not call the bean of that conversation
        assertSame(handlerA, Proxy.getInvocationHandler(greeterA));
        assertEquals(greetingA, greeterA.greet());
        assertEquals(greetingB, greeterB.greet());
        // the scope still holds the proxy
        conversationScope.current = "A";
        assertSame(greeterA, context.getBean("conversationGreeter"));
    }

    @Test
    public void refreshedProxyOutsideOfTheBasePackageIsNotCalledToRegisterIt() {
        Greeter greeter = (Greeter) context.getBean("springAopGreeter");
        InvocationHandler handler = Proxy.getInvocationHandler(greeter);
        String greeting = greeter.greet();
        String[] basePackagePrefixes = SpringPlugin.basePackagePrefixes;
        try {
            // no interface of the proxy is in the base package, so its target class would be asked
            SpringPlugin.basePackagePrefixes = new String[]{"com.example"};
            CountingBeanFactory countingBeanFactory = new CountingBeanFactory(beanFactory);
            JdkProxyRefresher.setHandler(greeter, new RefreshingInvocationHandler(countingBeanFactory,
                    new Class<?>[]{String.class}, new Object[]{"springAopGreeter"}, handler, null));

            // getBean() returns the proxy itself. A call of it to get its target class would get the bean again,
            // and so on until the stack overflows.
            assertEquals(greeting, greeter.greet());
            assertEquals(1, countingBeanFactory.calls);
            assertSame(handler, Proxy.getInvocationHandler(greeter));
        } finally {
            SpringPlugin.basePackagePrefixes = basePackagePrefixes;
        }
    }

    @Test
    public void basePackagePrefixIsCheckedAgainstTheTargetClassOfSpringAopProxy() {
        String[] basePackagePrefixes = SpringPlugin.basePackagePrefixes;
        try {
            // only the target class is in the base package, not the interface Greeter
            SpringPlugin.basePackagePrefixes = new String[]{GreeterImpl.class.getName()};

            assertTrue(JdkProxyRefresher.isTracked(context.getBean("prototypeSpringAopGreeter")));
            // the target of the proxy of another library is not known
            assertFalse(JdkProxyRefresher.isTracked(context.getBean("prototypeGreeter")));
        } finally {
            SpringPlugin.basePackagePrefixes = basePackagePrefixes;
        }
    }

    /**
     * A reload of the changed prototype. It is destroyed like a singleton, although the bean factory keeps no
     * instance of it.
     */
    private void reloadPrototype() {
        beanFactory.destroySingleton("prototypeGreeter");
        JdkProxyRefresher.refresh(beanFactory);
    }

    /**
     * A reload destroys the changed beans, creates the singletons again and refreshes the JDK proxies of the bean
     * factory.
     */
    private void reload(boolean createSingletons) {
        beanFactory.destroySingleton("springAopGreeter");
        beanFactory.destroySingleton("foreignGreeter");
        beanFactory.destroySingleton("greeterImpl");
        if (createSingletons) {
            beanFactory.preInstantiateSingletons();
        }
        JdkProxyRefresher.refresh(beanFactory);
    }
}
