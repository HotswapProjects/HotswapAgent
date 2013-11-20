package org.hotswap.agent.util;

import org.hotswap.agent.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.annotation.handler.*;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Process annotations of a plugin, register appropriate handlers.
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
        addAnnotationHandler(Transform.class, new TransformHandler(pluginManager));
        addAnnotationHandler(Watch.class, new WatchHandler(pluginManager));
    }

    public void addAnnotationHandler(Class<? extends Annotation> annotation, PluginHandler handler) {
        handlers.put(annotation, handler);
    }

    /**
     * Process annotations on the plugin class - only static methods, methods to hook plugin initialization.
     *
     * @param pluginClass plugin class
     * @return true if success
     */
    public boolean processAnnotations(Class pluginClass) {

        for (Field field : pluginClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                if (!processFieldAnnotations(null, field))
                    return false;

        }

        for (Method method : pluginClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()))
                if (!processMethodAnnotations(null, method))
                    return false;
        }

        return true;
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
        for (Annotation annotation : pluginClass.getDeclaredAnnotations()) {
            // TODO PluginSupport annotation
        }

        for (Field field : pluginClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()))
                if (!processFieldAnnotations(plugin, field))
                    return false;

        }

        for (Method method : pluginClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()))
                if (!processMethodAnnotations(plugin, method))
                    return false;

        }

        return true;
    }

    private boolean processFieldAnnotations(Object plugin, Field field) {
        // for all fields and all handlers
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
                if (annotation.annotationType().equals(handlerAnnotation)) {
                    // initialize
                    PluginAnnotation<?> pluginAnnotation = new PluginAnnotation(plugin, annotation, field);
                    if (!handlers.get(handlerAnnotation).initField(pluginAnnotation)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean processMethodAnnotations(Object plugin, Method method) {
        // for all methods and all handlers
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
                if (annotation.annotationType().equals(handlerAnnotation)) {
                    // initialize
                    PluginAnnotation<?> pluginAnnotation = new PluginAnnotation(plugin, annotation, method);
                    if (!handlers.get(handlerAnnotation).initMethod(pluginAnnotation)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
