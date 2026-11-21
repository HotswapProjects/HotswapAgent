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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.Path;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.core.ResourceMethodRegistry;
import org.jboss.resteasy.core.registry.RootClassNode;
import org.jboss.resteasy.core.registry.RootNode;
import org.jboss.resteasy.plugins.server.resourcefactory.POJOResourceFactory;
import org.jboss.resteasy.plugins.server.resourcefactory.SingletonResource;
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.util.GetRestful;

/**
 * Resteasy registers methods based the class path. Since the redefined class
 * may be in a different path or the method we need to iterate over known
 * bounded methods, reconstruct the class/method path and remove each method.
 *
 * Since the class/method path declarations may have not changed, we use the
 * registry removeRegistrations method also.
 *
 * The "original" class, does not seem to do the above trick...
 *
 * @author alpapad@gmail.com
 */
public class RefreshRegistryCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RefreshRegistryCommand.class);

    private static final String SPRING_RESOURCE_FACTORY = "org.jboss.resteasy.plugins.spring.SpringResourceFactory";

    private ClassLoader classLoader;

    private ServletContext context;

    private String className;

    private ServletContainerDispatcher servletContainerDispatcher;

    private Class<?> original;

    public void setupCmd(ClassLoader classLoader, Object context, Object servletContainerDispatcher, String className, Class<?> original) {
        this.classLoader = classLoader;
        this.context = (ServletContext) context;
        this.servletContainerDispatcher = (ServletContainerDispatcher) servletContainerDispatcher;
        this.className = className;
        this.original = original;
    }

    @Override
    public void executeCommand() {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

        LOGGER.debug("Re-Loading class: {} , {} , {}", className, oldClassLoader, classLoader);

        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Registry registry = (Registry) context.getAttribute(Registry.class.getName());
            if (registry == null) {
                registry = servletContainerDispatcher.getDispatcher().getRegistry();
            }
            // Keep how the resource was created, e.g. as a Spring bean by resteasy-spring. Registered again as
            // a plain per-request resource, its dependencies would not be injected anymore.
            List<ResourceFactory> resourceFactories = findResourceFactories(registry);

            Class<?> c = classLoader.loadClass(className);
            // without @Path anymore, removeRegistrations() would fail; its bindings are removed below
            boolean rootResource = GetRestful.isRootResource(c);

            //Does original actually represent the... original class????
            if (original != null && GetRestful.isRootResource(original)) {
                registry.removeRegistrations(original);
            }

            //Remove all matching registrations (between old and new class..)
            if (rootResource) {
                registry.removeRegistrations(c);
            }

            //Iterate over all known methods for this className
            if (registry instanceof ResourceMethodRegistry) {
                ResourceMethodRegistry rm = ResourceMethodRegistry.class.cast(registry);
                Map<String, List<ResourceInvoker>> bounded = rm.getBounded();
                for (Entry<String, List<ResourceInvoker>> e : bounded.entrySet()) {
                    LOGGER.debug("Examining {}", e.getKey());
                    for (ResourceInvoker r : e.getValue()) {
                        if(LOGGER.isLevelEnabled(Level.DEBUG)){
                            LOGGER.debug("Examining {} for method {} in class {}", e.getKey(), r.getMethod().getName(),
                                    r.getMethod().getDeclaringClass());
                        }
                        if (isRegisteredFor(r)) {
                            removeRegistration(rm, e.getKey(), r.getMethod());
                        }
                    }
                }
            }

            //Add the new resource
            if (!rootResource) {
                // e.g. its @Path was removed
                LOGGER.debug("{} is not a root resource anymore, it is not registered again", className);
                return;
            }
            boolean perRequest = resourceFactories.isEmpty();
            for (ResourceFactory resourceFactory : resourceFactories) {
                if (resourceFactory instanceof POJOResourceFactory) {
                    // its own class, which may implement or extend the reloaded class with @Path
                    Class<?> resourceClass = resourceFactory.getScannableClass();
                    if (resourceClass.getName().equals(className)) {
                        perRequest = true;
                    } else {
                        LOGGER.debug("Registering {} again per request", resourceClass.getName());
                        registry.addPerRequestResource(resourceClass);
                    }
                } else if (resourceFactory instanceof SingletonResource) {
                    // the same instance, with the metadata of the redefined class (e.g. a new @Context field)
                    LOGGER.debug("Registering the singleton {} again", className);
                    Object singleton = resourceFactory.createResource(null, null, null);
                    resourceFactory.unregistered();
                    registry.addSingletonResource(singleton);
                } else {
                    // e.g. the context of a SpringResourceFactory set by a ResteasyRegistration of resteasy-spring;
                    // the base path of other registrations is not known, as for a per-request resource
                    LOGGER.debug("Registering {} again with {}", className, resourceFactory);
                    resourceFactory.unregistered();
                    registry.addResourceFactory(resourceFactory, getContext(resourceFactory));
                }
            }
            if (perRequest) {
                registry.addPerRequestResource(c);
            }
        } catch (Exception e) {
            LOGGER.error("Could not reload rest class {}", e, className);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    /**
     * Returns the resource factories registered for the class, each once, in the order they are found. Matched by the
     * class the factory registered, the methods may be declared by a superclass, or inherited by a registered
     * subclass. A resource registered with its own ResourceClass (built with ResourceBuilder) is registered again
     * from its annotations, such a resource usually has no @Path and is not reloaded by the plugin.
     */
    private List<ResourceFactory> findResourceFactories(Registry registry) {
        List<ResourceFactory> resourceFactories = new ArrayList<>();
        if (!(registry instanceof ResourceMethodRegistry)) {
            return resourceFactories;
        }
        Set<ResourceFactory> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<ResourceInvoker> invokers : ((ResourceMethodRegistry) registry).getBounded().values()) {
            for (ResourceInvoker invoker : invokers) {
                ResourceFactory resourceFactory = getResourceFactory(invoker);
                if (resourceFactory != null && isRegisteredFor(invoker) && found.add(resourceFactory)) {
                    resourceFactories.add(resourceFactory);
                }
            }
        }
        return resourceFactories;
    }

    // the context of a SpringResourceFactory of resteasy-spring or a subclass (the base path of a
    // ResteasyRegistration), else null
    private static String getContext(ResourceFactory resourceFactory) {
        boolean springResourceFactory = false;
        for (Class<?> c = resourceFactory.getClass(); c != null && !springResourceFactory; c = c.getSuperclass()) {
            springResourceFactory = c.getName().equals(SPRING_RESOURCE_FACTORY);
        }
        if (!springResourceFactory) {
            return null;
        }
        try {
            Method getContext = resourceFactory.getClass().getMethod("getContext");
            getContext.setAccessible(true);
            return (String) getContext.invoke(resourceFactory);
        } catch (Exception e) {
            LOGGER.debug("Unable to get the context of resource factory {}", e, resourceFactory);
            return null;
        }
    }

    /**
     * Checks if the invoker was registered for the class: by the root resource class (with @Path) it was registered
     * for, as its methods may be declared by a superclass or inherited by a registered subclass, and a Spring bean
     * class may extend the class with @Path, else by the class declaring its method.
     */
    private boolean isRegisteredFor(ResourceInvoker invoker) {
        Class<?> registeredClass = getRegisteredClass(invoker);
        if (registeredClass == null) {
            registeredClass = invoker.getMethod().getDeclaringClass();
        }
        return registeredClass.getName().equals(className);
    }

    // The class the invoker was registered for, kept by its ResourceMethod or ResourceLocator: the root resource class
    // (with @Path) of the class of the factory. Not taken from the factory, which may e.g. look it up in JNDI.
    private static Class<?> getRegisteredClass(ResourceInvoker invoker) {
        try {
            Object method = null;
            for (Class<?> c = invoker.getClass(); c != null && method == null; c = c.getSuperclass()) {
                try {
                    Field field = c.getDeclaredField("method");
                    field.setAccessible(true);
                    method = field.get(invoker);
                } catch (NoSuchFieldException e) {
                    // try the superclass
                }
            }
            if (method == null) {
                return null;
            }
            Object resourceClass = method.getClass().getMethod("getResourceClass").invoke(method);
            if (resourceClass == null) {
                return null;
            }
            return (Class<?>) resourceClass.getClass().getMethod("getClazz").invoke(resourceClass);
        } catch (Exception e) {
            LOGGER.debug("Unable to get the registered class of {}", e, invoker);
            return null;
        }
    }

    private ResourceFactory getResourceFactory(ResourceInvoker invoker) {
        // ResourceMethodInvoker and ResourceLocatorInvoker keep it in the field "resource"
        for (Class<?> c = invoker.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("resource");
                f.setAccessible(true);
                Object value = f.get(invoker);
                return value instanceof ResourceFactory ? (ResourceFactory) value : null;
            } catch (NoSuchFieldException e) {
                // try the superclass
            } catch (Exception e) {
                LOGGER.debug("Unable to get the resource factory of {}", e, invoker);
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T get(ResourceMethodRegistry rm, String field) {
        Class<?> c;
        try {
            c = classLoader.loadClass(ResourceMethodRegistry.class.getName());
            Field f = c.getField(field);
            return (T) f.get(rm);
        } catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException e) {
            LOGGER.error("Could not get field {}", e, field);
        }

        return null;
    }

    private void removeRegistration(ResourceMethodRegistry rm, String path, Method method) {
        try {
            if (rm.isWiderMatching()) {
                RootNode rootNode = get(rm, "rootNode");
                rootNode.removeBinding(path, method);
            } else {
                // a resource method without @Path is bound to the path of its class
                Path methodPathAnnotation = method.getAnnotation(Path.class);
                String methodpath = methodPathAnnotation != null ? methodPathAnnotation.value() : "";
                // the method path ends the path, it may also appear in the path of the class
                String classExpression = path.endsWith(methodpath)
                        ? path.substring(0, path.length() - methodpath.length()) : path;
                if (classExpression.endsWith("/")) {
                    classExpression = classExpression.substring(0, classExpression.length() - 1);
                }
                RootClassNode root = get(rm, "root");
                root.removeBinding(classExpression, path, method);
            }
        } catch (Exception e) {
            LOGGER.error("Could not remove method registration from path {}, {}", e, path, method);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((classLoader == null) ? 0 : classLoader.hashCode());
        result = prime * result + ((className == null) ? 0 : className.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RefreshRegistryCommand other = (RefreshRegistryCommand) obj;
        if (classLoader == null) {
            if (other.classLoader != null)
                return false;
        } else if (!classLoader.equals(other.classLoader))
            return false;
        if (className == null) {
            if (other.className != null)
                return false;
        } else if (!className.equals(other.className))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "RefreshRegistryCommand [classLoader=" + classLoader + ", className=" + className + "]";
    }
}