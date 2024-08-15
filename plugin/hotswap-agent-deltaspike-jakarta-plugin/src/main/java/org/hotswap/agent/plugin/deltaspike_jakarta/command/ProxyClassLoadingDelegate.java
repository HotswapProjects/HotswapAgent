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

import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Delegates proxy loading to AsmProxyClassGenerator or PluginManager.getInstance()
 *
 * @author Vladimir Dvorak
 */
public class ProxyClassLoadingDelegate {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyClassLoadingDelegate.class);

    private static final ThreadLocal<Boolean> MAGIC_IN_PROGRESS = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static final void beginProxyRegeneration() {
        MAGIC_IN_PROGRESS.set(true);
    }

    public static final void endProxyRegeneration() {
        MAGIC_IN_PROGRESS.remove();
    }

    public static Class<?> tryToLoadClassForName(String proxyClassName, Class<?> targetClass, ClassLoader classLoader) {
        if (MAGIC_IN_PROGRESS.get()) {
            return null;
        }
        return (Class<?>) ReflectionHelper.invoke(null, org.apache.deltaspike.core.util.ClassUtils.class, "tryToLoadClassForName",
                new Class[] { String.class, Class.class, ClassLoader.class },
                proxyClassName, targetClass, classLoader);
    }

    public static Class<?> loadClass(ClassLoader loader, String className, byte[] bytes, ProtectionDomain protectionDomain) {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(className);
                try {
                    Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                    reloadMap.put(originalProxyClass, bytes);
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                //it has not actually been loaded yet
            }
        }
        try {
            Class<?> proxyClassGeneratorClass = loader.loadClass("org.apache.deltaspike.proxy.impl.AsmDeltaSpikeProxyClassGenerator");
            if (proxyClassGeneratorClass != null) {
                return (Class<?>) ReflectionHelper.invoke(null, proxyClassGeneratorClass, "loadClass",
                        new Class[]{ClassLoader.class, String.class, byte[].class, ProtectionDomain.class},
                        loader, className, bytes, protectionDomain);
            }
        } catch (Exception e) {
            LOGGER.error("loadClass() exception {}", e.getMessage());
        }
        return null;
    }

    public static Class<?> defineClass(ClassLoader loader, String className, byte[] bytes, Class<?> originalClass, ProtectionDomain protectionDomain) {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(className);
                try {
                    Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                    reloadMap.put(originalProxyClass, bytes);
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                //it has not actually been loaded yet
            }
        }
        try {
            Class<?> classDefiner = loader.loadClass("org.apache.deltaspike.proxy.impl.ClassDefiner");;
            if (classDefiner != null) {
                return (Class<?>) ReflectionHelper.invoke(null, classDefiner, "defineClass",
                        new Class[]{ClassLoader.class, String.class, byte[].class, Class.class, ProtectionDomain.class},
                        loader, className, bytes, originalClass, protectionDomain);
            }
        } catch (Exception e) {
            LOGGER.error("loadClass() exception {}", e.getMessage());
        }
        return null;
    }

}
