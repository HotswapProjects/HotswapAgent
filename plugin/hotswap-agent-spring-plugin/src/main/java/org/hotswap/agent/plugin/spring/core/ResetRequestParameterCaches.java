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
package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Support for Spring MVC Parameter caches.
 */
public class ResetRequestParameterCaches {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ResetRequestParameterCaches.class);

    private static Set<Object> localParameterNameDiscovers = new HashSet<>();

    private static Class<?> getLocalVariableTableParameterNameDiscovererClassOrNull() {
        try {
            //This is probably a bad idea as Class.forName has lots of issues but this was easiest for now.
            return Class.forName("org.springframework.core.LocalVariableTableParameterNameDiscoverer");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("LocalVariableTableParameterNameDiscoverer class not found");
            return null;
        }
    }

    public static void setLocalParameterNameDiscovers(Object localVariableNameDiscoverer) {
        localParameterNameDiscovers.add(localVariableNameDiscoverer);
    }

    public static void reset(DefaultListableBeanFactory beanFactory, Set<Class<?>> clazzes) {

        Class<?> c = getLocalVariableTableParameterNameDiscovererClassOrNull();
        if (c == null) {
            return;
        }

        try {
            for (Object localParameterNameDiscover : localParameterNameDiscovers) {
                Object parameterNamesCacheObj = ReflectionHelper.getNoException(localParameterNameDiscover, c, "parameterNamesCache");
                if (parameterNamesCacheObj == null) {
                    return;
                }
                if (!(parameterNamesCacheObj instanceof Map)) {
                    return;
                }

                Map<Object, Object> parameterNamesCache = (Map<Object, Object>) parameterNamesCacheObj;
                for (Class clazz : clazzes) {
                    parameterNamesCache.remove(clazz);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to clear parameterNamesCache", e);
        }
    }

}
