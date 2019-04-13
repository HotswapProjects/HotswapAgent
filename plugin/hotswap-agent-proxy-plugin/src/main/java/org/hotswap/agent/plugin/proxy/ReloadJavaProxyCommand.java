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
package org.hotswap.agent.plugin.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.java.ProxyGenerator;

/**
 * Joins subsequent proxy redefinition commands together and guarantee execution order
 */
public class ReloadJavaProxyCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ReloadJavaProxyCommand.class);

    private ClassLoader classLoader;
    private String className;
    private Map<String, String> signatureMapOrig;

    public ReloadJavaProxyCommand(ClassLoader classLoader, String className, Map<String, String> signatureMapOrig) {
        this.classLoader = classLoader;
        this.classLoader = classLoader;
        this.className = className;
        this.className = className;
        this.signatureMapOrig = signatureMapOrig;
    }

    public void executeCommand() {
        try {
            executeSingleCommand();
            List<Command> commands = new ArrayList<>(getMergedCommands());
            for (Command command: commands) {
                ((ReloadJavaProxyCommand) command).executeSingleCommand();
            }
        } finally {
            ProxyPlugin.reloadFlag = false;
        }
    }

    public void executeSingleCommand() {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            Map<String, String> signatureMap = ProxyClassSignatureHelper.getNonSyntheticSignatureMap(clazz);
            if (!signatureMap.equals(signatureMapOrig)) {
                byte[] generateProxyClass = ProxyGenerator.generateProxyClass(className, clazz.getInterfaces());
                Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                reloadMap.put(clazz, generateProxyClass);
                PluginManager.getInstance().hotswap(reloadMap);
                LOGGER.reload("Class '{}' has been reloaded.", className);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error redefining java proxy {}", e, className);
        }
    }

    public boolean shiftScheduleTime() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReloadJavaProxyCommand that = (ReloadJavaProxyCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ReloadJavaProxyCommand{" + "classLoader=" + classLoader + '}';
    }

}
