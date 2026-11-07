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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.core.ResourceMethodRegistry;
import org.jboss.resteasy.plugins.server.resourcefactory.POJOResourceFactory;
import org.jboss.resteasy.plugins.server.resourcefactory.SingletonResource;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Test;

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

    /**
     * A resource created by another factory (e.g. as a Spring bean by resteasy-spring) must keep its factory,
     * otherwise its dependencies are not injected after the reload.
     */
    @Test
    public void keepResourceFactory() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        ResourceFactory factory = new SingletonResource(new ManagedResource());
        registry.addResourceFactory(factory);

        refresh(registry, ManagedResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, ManagedResource.class);
        assertEquals(1, factories.size());
        assertSame(factory, factories.get(0));
    }

    @Test
    public void perRequestResourceStaysPerRequest() throws Exception {
        ResourceMethodRegistry registry = new ResourceMethodRegistry(new ResteasyProviderFactory());
        registry.addPerRequestResource(PlainResource.class);

        refresh(registry, PlainResource.class);

        List<ResourceFactory> factories = resourceFactories(registry, PlainResource.class);
        assertEquals(1, factories.size());
        assertTrue(factories.get(0) instanceof POJOResourceFactory);
    }

    private static void refresh(Registry registry, Class<?> resourceClass) {
        ServletContext context = (ServletContext) Proxy.newProxyInstance(RefreshRegistryCommandTest.class.getClassLoader(),
                new Class[]{ServletContext.class},
                (proxy, method, args) -> method.getName().equals("getAttribute") && Registry.class.getName().equals(args[0])
                        ? registry : null);
        RefreshRegistryCommand command = new RefreshRegistryCommand();
        command.setupCmd(resourceClass.getClassLoader(), context, null, resourceClass.getName(), resourceClass);
        command.executeCommand();
    }

    private static List<ResourceFactory> resourceFactories(ResourceMethodRegistry registry, Class<?> resourceClass)
            throws Exception {
        List<ResourceFactory> factories = new ArrayList<>();
        for (List<ResourceInvoker> invokers : registry.getBounded().values()) {
            for (ResourceInvoker invoker : invokers) {
                if (invoker.getMethod().getDeclaringClass() == resourceClass) {
                    Field field = invoker.getClass().getDeclaredField("resource");
                    field.setAccessible(true);
                    factories.add((ResourceFactory) field.get(invoker));
                }
            }
        }
        return factories;
    }
}
