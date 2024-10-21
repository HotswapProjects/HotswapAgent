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
package org.hotswap.agent.plugin.spring.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.CollectionUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Support for Spring MVC mapping caches.
 */
public class ResetRequestMappingCaches {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetRequestMappingCaches.class);

    public static List<HandlerMapping> handlerMappings;
    public static Set<String> beansToProcess;
    public static Set<String> newBeanNames;
    public static DefaultListableBeanFactory beanFactory;

    private static Class<?> getHandlerMethodMappingClassOrNull() {
        try {
            //This is probably a bad idea as Class.forName has lots of issues but this was easiest for now.
            return Class.forName("org.springframework.web.servlet.handler.AbstractHandlerMethodMapping");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("HandlerMethodMapping class not found");
            return null;
        }
    }

    public static void reset(DefaultListableBeanFactory beanFactory,
                             Set<String> beansToProcess,
                             Set<String> newBeanNames) {
        ResetRequestMappingCaches.beansToProcess = beansToProcess;
        ResetRequestMappingCaches.newBeanNames = newBeanNames;
        ResetRequestMappingCaches.beanFactory = beanFactory;

        // Determine whether it is a spring mvc controller bean change
        if (!needReloadSpringMVC()) {
            LOGGER.trace("Spring: spring mvc no changes");
            return;
        }

        Class<?> abstractHandlerMethodMapping = getHandlerMethodMappingClassOrNull();
        if (abstractHandlerMethodMapping == null) {
            return;
        }

        try {
            // Handle Servlet mapping
            processServletMappings(abstractHandlerMethodMapping);
            // Handle Spring mapping
            processSpringMappings(abstractHandlerMethodMapping, beanFactory);
        } catch (Exception e) {
            LOGGER.error("Failed to clear HandlerMappings", e);
        }

    }

    private static boolean needReloadSpringMVC() {
        Set<String> allBeans = new HashSet<>();
        allBeans.addAll(ResetRequestMappingCaches.beansToProcess);
        allBeans.addAll(ResetRequestMappingCaches.newBeanNames);
        for (String beanName : allBeans) {
            Class<?> beanClass = ResetRequestMappingCaches.beanFactory.getType(beanName);
            if (beanClass == null) {
                LOGGER.trace("Spring: bean {} not found", beanName);
                continue;
            }
            if (isMvcHandler(beanClass)) {
                return true;
            }
        }
        return false;
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

    private static void processServletMappings(Class<?> abstractHandlerMethodMapping) throws Exception {
        if (CollectionUtils.isEmpty(handlerMappings)) {
            return;
        }
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (handlerMapping instanceof RequestMappingHandlerMapping) {
                processMappings(abstractHandlerMethodMapping, handlerMapping);
            }
        }
    }

    private static void processMappings(Class<?> abstractHandlerMethodMapping, Object handlerMapping) throws Exception {
        LOGGER.trace("clearing HandlerMapping for {}", handlerMapping.getClass());
        try {
            Field f = abstractHandlerMethodMapping.getDeclaredField("handlerMethods");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(handlerMapping)).clear();
            f = abstractHandlerMethodMapping.getDeclaredField("urlMap");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(handlerMapping)).clear();
            try {
                f = abstractHandlerMethodMapping.getDeclaredField("nameMap");
                f.setAccessible(true);
                ((Map<?, ?>) f.get(handlerMapping)).clear();
            } catch (NoSuchFieldException nsfe) {
                LOGGER.trace("Probably using Spring 4.0 or below: {}", nsfe.getMessage());
            }
            if (handlerMapping instanceof InitializingBean) {
                ((InitializingBean) handlerMapping).afterPropertiesSet();
            }
        } catch (NoSuchFieldException nsfe) {
            LOGGER.trace("Probably using Spring 4.2+", nsfe.getMessage());
            Method m = abstractHandlerMethodMapping.getDeclaredMethod("getHandlerMethods");
            Class<?>[] parameterTypes = new Class[1];
            parameterTypes[0] = Object.class;
            Method u = abstractHandlerMethodMapping.getDeclaredMethod("unregisterMapping", parameterTypes);
            Map<?, ?> unmodifiableHandlerMethods = (Map<?, ?>) m.invoke(handlerMapping);
            Object[] keys = unmodifiableHandlerMethods.keySet().toArray();
            for (Object key : keys) {
                HandlerMethod handlerMethod = (HandlerMethod) unmodifiableHandlerMethods.get(key);
                if (ResetRequestMappingCaches.beansToProcess.contains(handlerMethod.getBean().toString())) {
                    LOGGER.trace("Unregistering handler method {}", key);
                    // Uninstall mapping
                    u.invoke(handlerMapping, key);
                }
            }
            // Register mapping manually
            registerHandlerBeans(ResetRequestMappingCaches.beansToProcess, abstractHandlerMethodMapping, handlerMapping);
            registerHandlerBeans(ResetRequestMappingCaches.newBeanNames, abstractHandlerMethodMapping, handlerMapping);
        }
    }

    private static void registerHandlerBeans(Set<String> beanNames,
                                             Class<?> abstractHandlerMethodMapping,
                                             Object handlerMapping) throws Exception {
        for (String beanName : beanNames) {
            Class<?> beanClass = ResetRequestMappingCaches.beanFactory.getType(beanName);
            if (beanClass == null) {
                LOGGER.warning("Spring: bean {} not found", beanName);
                continue;
            }
            if (isMvcHandler(beanClass)) {
                LOGGER.info("Spring: registering handler bean {}", beanName);
                Method detectHandlerMethods = abstractHandlerMethodMapping.getDeclaredMethod("detectHandlerMethods", Object.class);
                detectHandlerMethods.setAccessible(true);
                detectHandlerMethods.invoke(handlerMapping, beanName);
            }
        }
    }

    private static boolean isMvcHandler(Class<?> beanClass) {
        // Determine whether there is @Controller annotation or its derived annotation on the class
        if (beanClass.isAnnotationPresent(Controller.class) || beanClass.isAnnotationPresent(RestController.class)) {
            return true;
        }

        // Determine whether the class implements the controller interface in Spring MVC
        if (Controller.class.isAssignableFrom(beanClass)) {
            return true;
        }

        // Determine whether the method uses relevant annotations in Spring MVC
        for (Method method : beanClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(RequestMapping.class) ||
                    method.isAnnotationPresent(GetMapping.class) ||
                    method.isAnnotationPresent(DeleteMapping.class) ||
                    method.isAnnotationPresent(PatchMapping.class) ||
                    method.isAnnotationPresent(PutMapping.class) ||
                    method.isAnnotationPresent(PostMapping.class)) {
                return true;
            }
        }

        return false;
    }

    /**
     * DispatcherServlet instantiation registration HandlerMapping
     *
     * @param handlerMappings
     */
    public static void setHandlerMappings(List<HandlerMapping> handlerMappings) {
        ResetRequestMappingCaches.handlerMappings = handlerMappings;
    }

}
