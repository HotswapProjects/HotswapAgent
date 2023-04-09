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
package org.hotswap.agent.plugin.elresolver;

import java.lang.reflect.Method;
import java.util.Set;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Purge caches in registered class loaders. It calls __purgeClassCache(...) injected to BeanELResolver in ELResolverPlugin.
 *
 */
public class PurgeBeanELResolverCacheCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeBeanELResolverCacheCommand.class);

    private ClassLoader appClassLoader;

    private Set<Object> registeredBeanELResolvers;

    public PurgeBeanELResolverCacheCommand(ClassLoader appClassLoader, Set<Object> registeredBeanELResolvers)
    {
        this.appClassLoader = appClassLoader;
        this.registeredBeanELResolvers = registeredBeanELResolvers;
    }

    @Override
    public void executeCommand() {
        LOGGER.debug("Purging BeanELResolver cache.");
        try {
            Class<?> beanElResolverClass = Class.forName("javax.el.BeanELResolver", true, appClassLoader);
            Method beanElResolverMethod = beanElResolverClass.getDeclaredMethod(ELResolverPlugin.PURGE_CLASS_CACHE_METHOD_NAME, ClassLoader.class);
            for (Object registeredBeanELResolver : registeredBeanELResolvers) {
                beanElResolverMethod.invoke(registeredBeanELResolver, appClassLoader);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("BeanELResolver class not found in class loader {}.", appClassLoader);
        } catch (Exception e) {
            LOGGER.error("Error purging BeanELResolver cache.", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PurgeBeanELResolverCacheCommand that = (PurgeBeanELResolverCacheCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PurgeBeanELResolverCacheCommand{appClassLoader=" + appClassLoader + '}';
    }
}
