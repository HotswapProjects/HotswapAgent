/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.reload;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Registers @EventListener methods of reloaded and new beans.
 * <p>
 * Spring finds @EventListener methods only once, in EventListenerMethodProcessor.afterSingletonsInstantiated()
 * at the end of context refresh. Beans recreated by the plugin are not processed again, so a listener method added
 * to a bean never receives events, and a removed or changed one stays registered.
 * <p>
 * Classes are used by name, EventListenerMethodProcessor exists since Spring 4.2.
 */
public class EventListenerReload {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(EventListenerReload.class);

    private static final String PROCESSOR_CLASS = "org.springframework.context.event.EventListenerMethodProcessor";
    private static final String ADAPTER_CLASS = "org.springframework.context.event.ApplicationListenerMethodAdapter";

    public static void reloadEventListeners(DefaultListableBeanFactory beanFactory, Collection<String> beanNames) {
        if (beanNames.isEmpty()) {
            return;
        }
        Class<?> processorClass;
        Class<?> adapterClass;
        try {
            ClassLoader classLoader = DefaultListableBeanFactory.class.getClassLoader();
            processorClass = Class.forName(PROCESSOR_CLASS, false, classLoader);
            adapterClass = Class.forName(ADAPTER_CLASS, false, classLoader);
        } catch (ClassNotFoundException e) {
            LOGGER.trace("Spring without @EventListener support, skip event listener reload");
            return;
        }

        Map<String, ?> processors = beanFactory.getBeansOfType(processorClass, false, false);
        for (Object processor : processors.values()) {
            try {
                reloadEventListeners(beanFactory, beanNames, processor, adapterClass);
            } catch (Exception e) {
                LOGGER.error("Failed to reload @EventListener methods of beans {}", e, beanNames);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void reloadEventListeners(DefaultListableBeanFactory beanFactory, Collection<String> beanNames,
                                             Object processor, Class<?> adapterClass) throws Exception {
        Object context = getField(processor, "applicationContext");
        if (!(context instanceof AbstractApplicationContext)) {
            LOGGER.debug("Unknown application context {} of {}, skip event listener reload", context, processor);
            return;
        }
        AbstractApplicationContext applicationContext = (AbstractApplicationContext) context;
        ApplicationEventMulticaster multicaster = beanFactory.getBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        Set<Class<?>> nonAnnotatedClasses = (Set<Class<?>>) getField(processor, "nonAnnotatedClasses");
        Method processBean = findProcessBean(processor.getClass());
        if (processBean == null) {
            LOGGER.debug("EventListenerMethodProcessor.processBean not found, skip event listener reload");
            return;
        }

        for (String beanName : beanNames) {
            if (!beanFactory.containsBeanDefinition(beanName) || beanName.startsWith("scopedTarget.")) {
                continue;
            }
            Class<?> type = beanFactory.getType(beanName);
            if (type == null) {
                continue;
            }
            Class<?> targetType = ClassUtils.getUserClass(type);

            // listeners found for the old class
            for (ApplicationListener<?> listener : new ArrayList<>(applicationContext.getApplicationListeners())) {
                if (adapterClass.isInstance(listener) && beanName.equals(getField(listener, "beanName"))) {
                    applicationContext.getApplicationListeners().remove(listener);
                    multicaster.removeApplicationListener(listener);
                }
            }
            // the class had no @EventListener method before
            if (nonAnnotatedClasses != null) {
                nonAnnotatedClasses.remove(targetType);
            }

            if (processBean.getParameterTypes().length == 2) {
                processBean.invoke(processor, beanName, targetType);
            } else {
                Object factories = processor.getClass().getMethod("getEventListenerFactories").invoke(processor);
                processBean.invoke(processor, factories, beanName, targetType);
            }
            LOGGER.debug("@EventListener methods of bean '{}' reloaded", beanName);
        }
    }

    private static Method findProcessBean(Class<?> processorClass) {
        for (Class<?> clazz = processorClass; clazz != null; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getName().equals("processBean")) {
                    // Spring 5+: processBean(String, Class), Spring 4.2/4.3: processBean(List, String, Class)
                    if ((types.length == 2 && types[0] == String.class && types[1] == Class.class)
                            || (types.length == 3 && types[0] == List.class && types[1] == String.class)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static Object getField(Object target, String name) throws IllegalAccessException {
        for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                // try superclass
            }
        }
        return null;
    }
}
