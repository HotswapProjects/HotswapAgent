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
package org.hotswap.agent.plugin.spring.reader;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class ComponentClassRefreshCommand extends MergeableCommand {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ComponentClassRefreshCommand.class);

    private final ClassLoader classLoader;
    private final String componentClass;
    private final byte[] classDefinition;


    public ComponentClassRefreshCommand(ClassLoader classLoader, String componentClass, byte[] classDefinition) {
        this.classLoader = classLoader;
        this.componentClass = componentClass;
        this.classDefinition = classDefinition;
    }

    @Override
    public void executeCommand() {
        LOGGER.debug("Executing AnnotatedBeanDefinitionReaderAgent.refreshClass('{}')", componentClass);

        try {
            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.reader.AnnotatedBeanDefinitionReaderAgent", true, classLoader);
            Method method = clazz.getMethod("refreshClass", ClassLoader.class, String.class, byte[].class);
            method.invoke(null, classLoader, componentClass, classDefinition);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, componentClass, classLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentClassRefreshCommand that = (ComponentClassRefreshCommand) o;
        return Objects.equals(classLoader, that.classLoader) && Objects.equals(componentClass, that.componentClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classLoader, componentClass);
    }

    @Override
    public String toString() {
        return "ComponentClassRefreshCommand{" +
                "classLoader=" + classLoader +
                ", componentClass='" + componentClass + '\'' +
                '}';
    }
}
