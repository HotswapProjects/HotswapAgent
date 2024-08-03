/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.deltaspike_jakarta.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike_jakarta.DeltaSpikeJakartaPlugin;

/**
 * The Class PartialBeanClassRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class PartialBeanClassRefreshCommand extends MergeableCommand  {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PartialBeanClassRefreshCommand.class);

    ClassLoader appClassLoader;
    Class<?> beanClass;
    private Scheduler scheduler;
    String oldSignForProxyCheck;
    List<Command> chainedCommands = new ArrayList<>();

    public PartialBeanClassRefreshCommand(ClassLoader classLoader, Class<?> beanClass, String oldSignForProxyCheck, Scheduler scheduler) {
        this.appClassLoader = classLoader;
        this.beanClass = beanClass;
        this.oldSignForProxyCheck = oldSignForProxyCheck;
        this.scheduler = scheduler;
    }

    public void addChainedCommand(Command cmd) {
        chainedCommands.add(cmd);
    }

    @Override
    public void executeCommand() {
        boolean reloaded = false;

        try {
            LOGGER.debug("Executing PartialBeanClassRefreshAgent.refreshPartialBeanClass('{}')", beanClass.getName());
            Class<?> agentClazz = Class.forName(PartialBeanClassRefreshAgent.class.getName(), true, appClassLoader);
            Method m  = agentClazz.getDeclaredMethod("refreshPartialBeanClass", new Class[] {ClassLoader.class,  Class.class , String.class});
            m.invoke(null, appClassLoader, beanClass, oldSignForProxyCheck);
            reloaded = true;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in appClassLoader {}", e, beanClass.getName(), appClassLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
        }
        if (reloaded) {
            for (Command cmd: chainedCommands) {
                scheduler.scheduleCommand(cmd, DeltaSpikeJakartaPlugin.WAIT_ON_REDEFINE);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PartialBeanClassRefreshCommand that = (PartialBeanClassRefreshCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;
        if (!beanClass.equals(that.beanClass)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        result = 31 * result + beanClass.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PartialBeanClassRefreshCommand{" +
            "appClassLoader=" + appClassLoader +
            ", partialBean='" + beanClass + '\'' +
            '}';
    }

}
