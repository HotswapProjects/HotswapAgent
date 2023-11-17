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
        this.className = className;
        this.signatureMapOrig = signatureMapOrig;
    }

    @Override
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
            LOGGER.debug("executeSingleCommand class:{}, signature equals:{}", className, signatureMap.equals(signatureMapOrig));
            if (!signatureMap.equals(signatureMapOrig) || !isImplementInterface(signatureMap, clazz)) {
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

    // Whether all methods of the interface are implemented
    private boolean isImplementInterface(Map<String, String> signatureMap, Class<?> clazz) {
        String clazzSignature = "";
        try {
            clazzSignature = ProxyClassSignatureHelper.getJavaClassSignature(clazz).replaceAll("final ", "");
            LOGGER.debug("clazzSignature: {}", clazzSignature);
        } catch (Exception e) {
            LOGGER.error("Error getJavaClassSignature {}", clazz, e);
            return true;
        }
        for (Map.Entry<String, String> entry : signatureMap.entrySet()) {
            if(clazzSignature.contains(entry.getKey()) && entry.getValue().contains("public abstract")) {
                LOGGER.debug("{} Signature: {}", entry.getKey(), entry.getValue());
                String[] methodSignatures = entry.getValue().replaceAll("abstract ", "").split(";");
                for (String methodSignature : methodSignatures) {
                    if(!clazzSignature.contains(methodSignature)) {
                        return false;
                    }
                }
            }
        }
        return true;
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
