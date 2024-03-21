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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.CollectionUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Support for Spring MVC mapping caches.
 */
public class ResetRequestMappingCaches {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetRequestMappingCaches.class);

    public static List<HandlerMapping> handlerMappings;

    private static Class<?> getHandlerMethodMappingClassOrNull() {
        try {
            //This is probably a bad idea as Class.forName has lots of issues but this was easiest for now.
            return Class.forName("org.springframework.web.servlet.handler.AbstractHandlerMethodMapping");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("HandlerMethodMapping class not found");
            return null;
        }
    }

    public static void reset(DefaultListableBeanFactory beanFactory) {
        Class<?> c = getHandlerMethodMappingClassOrNull();
        if (c == null) {
            return;
        }

        try {
            // 处理 Servlet 的映射
            processServletMappings(c);
            // 处理 Spring 的映射
            processSpringMappings(c, beanFactory);
        } catch (Exception e) {
            LOGGER.error("Failed to clear HandlerMappings", e);
        }

    }

    private static void processSpringMappings(Class<?> c, DefaultListableBeanFactory beanFactory) throws Exception {
        Map<String, ?> mappings =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(beanFactory, c, true, false);

        if (mappings.isEmpty()) {
            LOGGER.trace("Spring: no HandlerMappings found");
        }

        for (Entry<String, ?> e : mappings.entrySet()) {
            processMappings(c, e.getValue());
        }
    }

    private static void processServletMappings(Class<?> c) throws Exception {
        if (CollectionUtils.isEmpty(handlerMappings)) {
            return;
        }
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (handlerMapping instanceof RequestMappingHandlerMapping) {
                processMappings(c, handlerMapping);
            }
        }
    }

    private static void processMappings(Class<?> c, Object am) throws Exception {
        LOGGER.trace("clearing HandlerMapping for {}", am.getClass());
        try {
            Field f = c.getDeclaredField("handlerMethods");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(am)).clear();
            f = c.getDeclaredField("urlMap");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(am)).clear();
            try {
                f = c.getDeclaredField("nameMap");
                f.setAccessible(true);
                ((Map<?, ?>) f.get(am)).clear();
            } catch (NoSuchFieldException nsfe) {
                LOGGER.trace("Probably using Spring 4.0 or below: {}", nsfe.getMessage());
            }
        } catch (NoSuchFieldException nsfe) {
            LOGGER.trace("Probably using Spring 4.2+", nsfe.getMessage());
            Method m = c.getDeclaredMethod("getHandlerMethods");
            Class<?>[] parameterTypes = new Class[1];
            parameterTypes[0] = Object.class;
            Method u = c.getDeclaredMethod("unregisterMapping", parameterTypes);
            Map<?, ?> unmodifiableHandlerMethods = (Map<?, ?>) m.invoke(am);
            Object[] keys = unmodifiableHandlerMethods.keySet().toArray();
            unmodifiableHandlerMethods = null;
            for (Object key : keys) {
                LOGGER.trace("Unregistering handler method {}", key);
                u.invoke(am, key);
            }
        }
        if (am instanceof InitializingBean) {
            ((InitializingBean) am).afterPropertiesSet();
        }
    }

    public static void setHandlerMappings(List<HandlerMapping> handlerMappings) {
        ResetRequestMappingCaches.handlerMappings = handlerMappings;
    }

}
