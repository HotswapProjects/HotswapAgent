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
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.spi.Registry;

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
                        if (r.getMethod().getDeclaringClass().getName().equals(className)) {
                            removeRegistration(rm, e.getKey(), r.getMethod());
                        }
                    }
                }
            }

            //Add the new resource
            registry.addPerRequestResource(c);
        } catch (Exception e) {
            LOGGER.error("Could not reload rest class {}", e, className);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
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
                String methodpath = method.getAnnotation(Path.class).value();
                String classExpression = path.replace(methodpath, "");
                if (classExpression.endsWith("/")) {
                    classExpression.substring(0, classExpression.length() - 1);
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