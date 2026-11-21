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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
            ResourceFactory resourceFactory = findResourceFactory(registry);

            //Does original actually represent the... original class????
            if(original != null) {
                registry.removeRegistrations(original);
            }

            Class<?> c = classLoader.loadClass(className);

            //Remove all matching registrations (between old and new class..)
            registry.removeRegistrations(c);

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
            if (resourceFactory instanceof SingletonResource) {
                // the same instance, with the metadata of the redefined class (e.g. a new @Context field)
                LOGGER.debug("Registering the singleton {} again", className);
                registry.addSingletonResource(resourceFactory.createResource(null, null, null));
            } else if (resourceFactory != null && !(resourceFactory instanceof POJOResourceFactory)) {
                LOGGER.debug("Registering {} again with {}", className, resourceFactory);
                registry.addResourceFactory(resourceFactory);
            } else {
                registry.addPerRequestResource(c);
            }
        } catch (Exception e) {
            LOGGER.error("Could not reload rest class {}", e, className);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    /**
     * Returns the resource factory registered for the class, or null if none is registered. Matched by the class the
     * factory registered, the methods may be declared by a superclass, or inherited by a registered subclass.
     */
    private ResourceFactory findResourceFactory(Registry registry) {
        if (!(registry instanceof ResourceMethodRegistry)) {
            return null;
        }
        for (List<ResourceInvoker> invokers : ((ResourceMethodRegistry) registry).getBounded().values()) {
            for (ResourceInvoker invoker : invokers) {
                ResourceFactory resourceFactory = getResourceFactory(invoker);
                if (resourceFactory != null && isRegisteredFor(invoker)) {
                    return resourceFactory;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the invoker was registered for the class: by the class its resource factory registered, as its
     * methods may be declared by a superclass or inherited by a registered subclass, else by the class declaring its
     * method.
     */
    private boolean isRegisteredFor(ResourceInvoker invoker) {
        ResourceFactory resourceFactory = getResourceFactory(invoker);
        Class<?> registeredClass = resourceFactory != null ? resourceFactory.getScannableClass() : null;
        if (registeredClass == null) {
            registeredClass = invoker.getMethod().getDeclaringClass();
        }
        return registeredClass.getName().equals(className);
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
                String classExpression = path.replace(methodpath, "");
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