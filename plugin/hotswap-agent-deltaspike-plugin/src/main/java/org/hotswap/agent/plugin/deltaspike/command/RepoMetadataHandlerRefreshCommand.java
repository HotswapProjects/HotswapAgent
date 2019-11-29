/*
 * Copyright 2013-2019 the HotswapAgent authors.
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

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * The Class RepoMetadataHandlerRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class RepoMetadataHandlerRefreshCommand  extends MergeableCommand  {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepoMetadataHandlerRefreshCommand.class);

    ClassLoader appClassLoader;
    String repoClassName;

    public RepoMetadataHandlerRefreshCommand(ClassLoader appClassLoader, String repoClassName) {
        this.appClassLoader = appClassLoader;
        this.repoClassName = repoClassName;
    }

    @Override
    public void executeCommand() {
        try {
			LOGGER.debug("Executing RepoMetadataHandlerRefreshAgent.refreshHandler('{}')", repoClassName);
			Class<?> agentClazz = Class.forName(RepoMetadataHandlerRefreshAgent.class.getName(), true, appClassLoader);
			Method agentMethod  = agentClazz.getDeclaredMethod("refreshHandler", new Class[] { ClassLoader.class, String.class });
			agentMethod.invoke(null,
					appClassLoader,
					repoClassName
			);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing handler {} in classLoader {}", e, repoClassName, appClassLoader);
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

        RepoMetadataHandlerRefreshCommand that = (RepoMetadataHandlerRefreshCommand) o;

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
                "classLoader=" + appClassLoader +
                "repoClassName=" + repoClassName +
                '}';
    }

}
