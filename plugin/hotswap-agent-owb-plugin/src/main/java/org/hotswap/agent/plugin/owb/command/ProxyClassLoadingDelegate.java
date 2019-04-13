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
package org.hotswap.agent.plugin.owb.command;

import java.util.HashMap;
import java.util.Map;

import org.apache.webbeans.proxy.AbstractProxyFactory;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The OWB proxyFactory has its class loading tasks delegated to this class, which can then have some magic applied
 * to make OWB think that the class has not been loaded yet.
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

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        if (MAGIC_IN_PROGRESS.get()) {
            throw new ClassNotFoundException("HotswapAgent");
        }
        return Class.forName(name, initialize, loader);
    }

    public static Class<?> defineAndLoadClass(AbstractProxyFactory proxyFactory, ClassLoader classLoader, String proxyName, byte[] proxyBytes) {
        if (MAGIC_IN_PROGRESS.get()) {
            Class<?> reloaded = reloadProxyByteCode(classLoader, proxyName, proxyBytes);
            if (reloaded != null) {
                return reloaded;
            }
        }
        try {
            return (Class<?>) ReflectionHelper.invoke(proxyFactory, AbstractProxyFactory.class, "defineAndLoadClass",
                    new Class[]{ClassLoader.class, String.class, byte[].class},
                    classLoader, proxyName, proxyBytes);
        } catch (Exception e) {
            LOGGER.error("defineAndLoadClass() exception {}", e.getMessage());
        }
        return null;
    }

    public static Class<?> defineAndLoadClassWithUnsafe(Object unsafe, ClassLoader classLoader, String proxyName, byte[] proxyBytes) {
        if (MAGIC_IN_PROGRESS.get()) {
            Class<?> reloaded = reloadProxyByteCode(classLoader, proxyName, proxyBytes);
            if (reloaded != null) {
                return reloaded;
            }
        }
        try {
            return (Class<?>) ReflectionHelper.invoke(unsafe, unsafe.getClass(), "defineAndLoadClass",
                    new Class[]{ClassLoader.class, String.class, byte[].class},
                    classLoader, proxyName, proxyBytes);
        } catch (Exception e) {
            LOGGER.error("defineAndLoadClass() exception {}", e.getMessage());
        }
        return null;
    }

    private static Class<?> reloadProxyByteCode(ClassLoader classLoader, String proxyName, byte[] proxyBytes) {
        try {
            final Class<?> originalProxyClass = classLoader.loadClass(proxyName);
            try {
                Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                reloadMap.put(originalProxyClass, proxyBytes);
                // TODO : is this standard way how to reload class?
                PluginManager.getInstance().hotswap(reloadMap);
                return originalProxyClass;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (ClassNotFoundException e) {
            //it has not actually been loaded yet
        }
        return null;
    }
}
