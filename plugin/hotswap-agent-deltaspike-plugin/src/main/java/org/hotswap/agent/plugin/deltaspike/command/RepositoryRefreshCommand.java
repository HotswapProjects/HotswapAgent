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
package org.hotswap.agent.plugin.deltaspike.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.transformer.RepositoryTransformer;

/**
 * The Class RepositoryRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class RepositoryRefreshCommand  extends MergeableCommand  {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepositoryRefreshCommand.class);

    ClassLoader appClassLoader;
    String repoClassName;
    Object repositoryComponent;
    private List<Object> repositoryProxies;

    public RepositoryRefreshCommand(ClassLoader appClassLoader, String repoClassName, Object repositoryComponent) {
        this.appClassLoader = appClassLoader;
        this.repositoryComponent = repositoryComponent;
        this.repoClassName = repoClassName;
    }

    public RepositoryRefreshCommand(ClassLoader appClassLoader, String repoClassName, List<Object> repositoryProxies) {
        this.appClassLoader = appClassLoader;
        this.repoClassName = repoClassName;
        this.repositoryProxies = repositoryProxies;
    }

    @Override
    public void executeCommand() {
        if (repositoryComponent != null) {
            refreshRepository1();
        } else {
            refreshRepository2();
        }
    }

    // ds<1.9
    private void refreshRepository1() {
        try {
            Method reinitializeMethod = resolveClass("org.apache.deltaspike.data.impl.meta.RepositoryComponent")
                    .getDeclaredMethod(RepositoryTransformer.REINITIALIZE_METHOD);
            reinitializeMethod.invoke(repositoryComponent);
            LOGGER.info("Deltaspike repository {} refreshed.", repoClassName);
            RepositoryRefreshAgent.reloadFlag = true;
        } catch (Exception e) {
            LOGGER.error("Error reinitializing repository {}", e, repoClassName);
        }
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

    // ds>=1.9
    private void refreshRepository2() {
        try {
            LOGGER.debug( "Executing RepositoryRefreshAgent.refreshHandler('{}')", repoClassName);
            Class<?> agentClazz = Class.forName(
                    RepositoryRefreshAgent.class.getName(), true,
                    appClassLoader);
            Method agentMethod = agentClazz.getDeclaredMethod("refreshHandler", new Class[] { ClassLoader.class, String.class, List.class });
            agentMethod.invoke(null, appClassLoader, repoClassName, repositoryProxies);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing handler {} in appClassLoader {}", e, repoClassName, appClassLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RepositoryRefreshCommand that = (RepositoryRefreshCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;
        if (!repoClassName.equals(that.repoClassName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        result = 31 * result + repoClassName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PartialBeanClassRefreshCommand{" +
                "appClassLoader=" + appClassLoader +
                "repoClassName=" + repoClassName +
                '}';
    }

}
