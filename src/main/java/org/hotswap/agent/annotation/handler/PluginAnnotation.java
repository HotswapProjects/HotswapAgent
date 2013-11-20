package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Container for resolved annotation on a plugin.
 * <p/>
 * Either method OR field is filled - never both (annotation on method or field).
 *
 * @author Jiri Bubnik
 */
public class PluginAnnotation<T extends Annotation> {
    Object plugin;

    T annotation;

    Field field;

    Method method;

    public PluginAnnotation(Object plugin, T annotation, Method method) {
        this.plugin = plugin;
        this.annotation = annotation;
        this.method = method;
    }

    public PluginAnnotation(Object plugin, T annotation, Field field) {
        this.plugin = plugin;
        this.annotation = annotation;
        this.field = field;
    }

    /**
     * Resolve the plugin class (either from the plugin object or declaring class of field/method).
     *
     * @return plugin class
     */
    public Class getPluginClass() {
        if (plugin != null)
            return plugin.getClass();
        else if (field != null)
            return field.getDeclaringClass();
        else if (method != null)
            return method.getDeclaringClass();
        else
            throw new IllegalStateException("PluginAnnotation not correctly initialized.");
    }

    public Object getPlugin() {
        return plugin;
    }

    public T getAnnotation() {
        return annotation;
    }

    public Method getMethod() {
        return method;
    }

    public Field getField() {
        return field;
    }
}
