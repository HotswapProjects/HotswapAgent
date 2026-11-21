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
package org.hotswap.agent.plugin.resteasy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.bytecode.AnnotationsAttribute;
import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.core.ResourceMethodRegistry;
import org.jboss.resteasy.plugins.server.resourcefactory.POJOResourceFactory;
import org.jboss.resteasy.plugins.spring.SpringResourceFactory;
import org.jboss.resteasy.plugins.server.resourcefactory.SingletonResource;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import resteasytest.BeanOfRemovedPathResource;
import resteasytest.RemovedPathResource;

public class RefreshRegistryCommandTest {

    @Path("/managed")
    public static class ManagedResource {
        @GET
        public String get() {
            return "managed";
        }
    }

    @Path("/plain")
    public static class PlainResource {
        @GET
        public String get() {
            return "plain";
        }
    }

    @Path("/singleton")
    public static class SingletonResourceClass {
        @GET
        public String get() {
            return "singleton";
        }
    }

    /**
     * Declares the resource methods of {@link InheritingResource}, without @Path itself.
     */
    public static class ResourceMethods {
        @GET
        public String get() {
            return "inherited";
        }
    }

    @Path("/inheriting")
    public static class InheritingResource extends ResourceMethods {
    }

    @Path("/base")
    public static class BaseResource {
        @GET
        public String get() {
            return "base";
        }
    }

    @Path("/sub")
    public static class SubResource extends BaseResource {
    }

    @Path("/context")
    public static class ContextResource {
        @GET
        public String get() {
            return "context";
        }
    }

    @Path("/bean")
    public static class ResourceOfBean {
        @GET
        public String get() {
            return "bean";
        }
    }

    /**
     * The class of a Spring bean, without @Path itself.
     */
    public static class BeanOfResource extends ResourceOfBean {
    }

    @Path("/api")
    public interface Api {
        @GET
        String get();
    }

    public static class ApiImpl implements Api {
        @Override
        public String get() {
            return "api";
        }
    }

    @Path("/twice")
    public static class TwiceResource {
        @GET
        public String get() {
            return "twice";
        }
    }

    /**
     * Creates the resource another way than RESTEasy itself, as SpringResourceFactory of resteasy-spring.
     */
    public static class ManagedFactory implements ResourceFactory {
        private final Class<?> resourceClass;

        ManagedFactory(Class<?> resourceClass) {
            this.resourceClass = resourceClass;
        }

        @Override
        public Class<?> getScannableClass() {
            return resourceClass;
        }

        @Override
        public void registered(ResteasyProviderFactory factory) {
        }

        @Override
        public Object createResource(HttpRequest request, HttpResponse response, ResteasyProviderFactory factory) {
            try {
                return resourceClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void requestFinished(HttpRequest request, HttpResponse response, Object resource) {
        }

        @Override
        public void unregistered() {
        }
    }

    /**
     * A resource created by another factory (e.g. as a Spring bean by resteasy-spring) must keep its factory,
     * otherwise its dependencies are not injected after the reload.
     */
    @Test
    public void keepResourceFactory() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory factory = new ManagedFactory(ManagedResource.class);
        registry.addResourceFactory(factory);

        refresh(registry, ManagedResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/managed");
        assertEquals(1, factories.size());
        assertSame(factory, factories.get(0));
    }

    /**
     * The resource methods may be declared by a superclass without @Path.
     */
    @Test
    public void keepResourceFactoryOfResourceWithInheritedMethods() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory factory = new ManagedFactory(InheritingResource.class);
        registry.addResourceFactory(factory);

        refresh(registry, InheritingResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/inheriting");
        assertEquals(1, factories.size());
        assertSame(factory, factories.get(0));
    }

    /**
     * A reloaded resource class does not get the factory of a registered subclass, which inherits its methods.
     */
    @Test
    public void keepResourceFactoriesOfResourceAndSubclass() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory subFactory = new ManagedFactory(SubResource.class);
        ResourceFactory baseFactory = new ManagedFactory(BaseResource.class);
        registry.addResourceFactory(subFactory);
        registry.addResourceFactory(baseFactory);

        refresh(registry, BaseResource.class);

        List<ResourceFactory> baseFactories = resourceFactories(registry, "/base");
        assertEquals(1, baseFactories.size());
        assertSame(baseFactory, baseFactories.get(0));
        List<ResourceFactory> subFactories = resourceFactories(registry, "/sub");
        assertEquals(1, subFactories.size());
        assertSame(subFactory, subFactories.get(0));
    }

    /**
     * A singleton is registered again with the same instance, and with the metadata of the redefined class.
     */
    @Test
    public void singletonStaysTheSameInstance() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        SingletonResourceClass instance = new SingletonResourceClass();
        registry.addSingletonResource(instance);

        refresh(registry, SingletonResourceClass.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/singleton");
        assertEquals(1, factories.size());
        assertTrue(factories.get(0) instanceof SingletonResource);
        assertSame(instance, factories.get(0).createResource(null, null, null));
    }

    /**
     * A SpringResourceFactory with a context (the base path of a ResteasyRegistration) is registered again under it.
     */
    @Test
    public void keepContextOfSpringResourceFactory() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("contextResource", new ContextResource());
        SpringResourceFactory factory = new SpringResourceFactory("contextResource", beanFactory, ContextResource.class);
        factory.setContext("/v1");
        registry.addResourceFactory(factory, factory.getContext());

        refresh(registry, ContextResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/v1/context");
        assertEquals(1, factories.size());
        assertSame(factory, factories.get(0));
        assertEquals(0, resourceFactories(registry, "/context").size());
    }

    /**
     * The class of a Spring bean may extend the resource class with @Path, RESTEasy registers it under that class.
     */
    @Test
    public void keepResourceFactoryOfSubclassBean() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory factory = new ManagedFactory(BeanOfResource.class);
        registry.addResourceFactory(factory);

        refresh(registry, ResourceOfBean.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/bean");
        assertEquals(1, factories.size());
        assertSame(factory, factories.get(0));
    }

    /**
     * A per-request resource implementing a reloaded @Path interface is registered again with its own class.
     */
    @Test
    public void implementationOfReloadedInterfaceStaysPerRequest() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        registry.addPerRequestResource(ApiImpl.class);

        refresh(registry, Api.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/api");
        assertEquals(1, factories.size());
        assertTrue(factories.get(0) instanceof POJOResourceFactory);
        assertSame(ApiImpl.class, factories.get(0).getScannableClass());
    }

    /**
     * A class without @Path anymore is not registered again, and its old bindings are removed without an error, also
     * those of a Spring bean class extending it.
     */
    @Test
    public void classWithoutPathIsNotRegisteredAgain() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        registry.addResourceFactory(new ManagedFactory(BeanOfRemovedPathResource.class));
        assertEquals(1, resourceFactories(registry, "/removed").size());

        // the class as redefined without @Path
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));
        CtClass ctClass = classPool.get(RemovedPathResource.class.getName());
        ctClass.getClassFile().getAttributes().removeIf(attribute -> attribute instanceof AnnotationsAttribute);
        byte[] bytes = ctClass.toBytecode();
        ClassLoader redefined = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(RemovedPathResource.class.getName())) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
                    }
                }
                return super.loadClass(name, resolve);
            }
        };

        PrintStream out = System.out;
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        System.setOut(new PrintStream(log, true));
        try {
            refresh(registry, redefined, RemovedPathResource.class.getName());
        } finally {
            System.setOut(out);
        }
        assertEquals(0, resourceFactories(registry, "/removed").size());
        assertFalse(log.toString(), log.toString().contains("Could not"));
    }

    /**
     * All the factories registered for the class are registered again.
     */
    @Test
    public void keepAllResourceFactories() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory factory = new ManagedFactory(TwiceResource.class);
        registry.addResourceFactory(factory);
        registry.addPerRequestResource(TwiceResource.class);

        refresh(registry, TwiceResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/twice");
        assertEquals(2, factories.size());
        assertTrue(factories.contains(factory));
        assertTrue(factories.get(0) instanceof POJOResourceFactory || factories.get(1) instanceof POJOResourceFactory);
    }

    @Test
    public void perRequestResourceStaysPerRequest() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        registry.addPerRequestResource(PlainResource.class);

        refresh(registry, PlainResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, "/plain");
        assertEquals(1, factories.size());
        assertTrue(factories.get(0) instanceof POJOResourceFactory);
    }

    private static void refresh(Registry registry, Class<?> resourceClass) {
        refresh(registry, resourceClass.getClassLoader(), resourceClass.getName(), resourceClass);
    }

    // with the class loaded by the class loader, the original not known
    private static void refresh(Registry registry, ClassLoader classLoader, String className) {
        refresh(registry, classLoader, className, null);
    }

    private static void refresh(Registry registry, ClassLoader classLoader, String className, Class<?> original) {
        ServletContext context = (ServletContext) Proxy.newProxyInstance(RefreshRegistryCommandTest.class.getClassLoader(),
                new Class[]{ServletContext.class},
                (proxy, method, args) -> method.getName().equals("getAttribute") && Registry.class.getName().equals(args[0])
                        ? registry : null);
        RefreshRegistryCommand command = new RefreshRegistryCommand();
        command.setupCmd(classLoader, context, null, className, original);
        command.executeCommand();
    }

    // the resource factories of the methods bound to the path
    private static List<ResourceFactory> resourceFactories(ResourceMethodRegistry registry, String path)
            throws Exception {
        List<ResourceFactory> factories = new ArrayList<>();
        List<ResourceInvoker> invokers = registry.getBounded().get(path);
        if (invokers != null) {
            for (ResourceInvoker invoker : invokers) {
                Field field = invoker.getClass().getDeclaredField("resource");
                field.setAccessible(true);
                factories.add((ResourceFactory) field.get(invoker));
            }
        }
        return factories;
    }
}
