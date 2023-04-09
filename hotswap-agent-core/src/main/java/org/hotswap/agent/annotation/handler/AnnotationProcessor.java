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
package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Process annotations on a plugin, register appropriate handlers.
 *
 * @author Jiri Bubnik
 */
public class AnnotationProcessor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AnnotationProcessor.class);

    protected PluginManager pluginManager;

    public AnnotationProcessor(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        init(pluginManager);
    }

    protected Map<Class<? extends Annotation>, PluginHandler> handlers =
            new HashMap<Class<? extends Annotation>, PluginHandler>();

    public void init(PluginManager pluginManager) {
        addAnnotationHandler(Init.class, new InitHandler(pluginManager));
        addAnnotationHandler(OnClassLoadEvent.class, new OnClassLoadedHandler(pluginManager));
        addAnnotationHandler(OnClassFileEvent.class, new WatchHandler(pluginManager));
        addAnnotationHandler(OnResourceFileEvent.class, new WatchHandler(pluginManager));
    }

    public void addAnnotationHandler(Class<? extends Annotation> annotation, PluginHandler handler) {
        handlers.put(annotation, handler);
    }

    /**
     * Process annotations on the plugin class - only static methods, methods to hook plugin initialization.
     *
     * @param processClass class to process annotation
     * @param pluginClass main plugin class (annotated with @Plugin)
     * @return true if success
     */
    public boolean processAnnotations(Class processClass, Class pluginClass) {

        try {
            for (Field field : processClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    if (!processFieldAnnotations(null, field, pluginClass))
                        return false;

            }

            for (Method method : processClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()))
                    if (!processMethodAnnotations(null, method, pluginClass))
                        return false;
            }

            // process annotations on all supporting classes in addition to the plugin itself
            for (Annotation annotation : processClass.getDeclaredAnnotations()) {
                if (annotation instanceof Plugin) {
                    for (Class supportClass : ((Plugin) annotation).supportClass()) {
                        processAnnotations(supportClass, pluginClass);
                    }
                }
            }

            return true;
        } catch (Throwable e) {
            LOGGER.error("Unable to process plugin annotations '{}'", e, pluginClass);
            return false;
        }
    }

    /**
     * Process annotations on a plugin - non static fields and methods.
     *
     * @param plugin plugin object
     * @return true if success
     */
    public boolean processAnnotations(Object plugin) {
        LOGGER.debug("Processing annotations for plugin '" + plugin + "'.");

        Class pluginClass = plugin.getClass();

        for (Field field : pluginClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()))
                if (!processFieldAnnotations(plugin, field, pluginClass))
                    return false;

        }

        for (Method method : pluginClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()))
                if (!processMethodAnnotations(plugin, method, pluginClass))
                    return false;

        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean processFieldAnnotations(Object plugin, Field field, Class pluginClass) {
        // for all fields and all handlers
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
                if (annotation.annotationType().equals(handlerAnnotation)) {
                    // initialize
                    PluginAnnotation<?> pluginAnnotation = new PluginAnnotation<>(pluginClass, plugin, annotation, field);
                    if (!handlers.get(handlerAnnotation).initField(pluginAnnotation)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean processMethodAnnotations(Object plugin, Method method, Class pluginClass) {
        // for all methods and all handlers
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
                if (annotation.annotationType().equals(handlerAnnotation)) {
                    // initialize
                    PluginAnnotation<?> pluginAnnotation = new PluginAnnotation<>(pluginClass, plugin, annotation, method);
                    if (!handlers.get(handlerAnnotation).initMethod(pluginAnnotation)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
