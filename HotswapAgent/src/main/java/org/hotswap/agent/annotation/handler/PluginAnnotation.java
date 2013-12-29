package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * DTO for resolved plugin annotation properties.
 * <p/>
 * Either method OR field is filled - never both (annotation on method or field).
 *
 * @author Jiri Bubnik
 */
public class PluginAnnotation<T extends Annotation> {
    // target plugin object
    Object plugin;

    // the annotation to process
    T annotation;

    // annotation is on a field (and method property is empty)
    Field field;

    // annotation is on a method (and field property is empty)
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginAnnotation that = (PluginAnnotation) o;

        if (!annotation.equals(that.annotation)) return false;
        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (method != null ? !method.equals(that.method) : that.method != null) return false;
        if (!plugin.equals(that.plugin)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = plugin.hashCode();
        result = 31 * result + annotation.hashCode();
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (method != null ? method.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PluginAnnotation{" +
                "plugin=" + plugin +
                ", annotation=" + annotation +
                ", field=" + field +
                ", method=" + method +
                '}';
    }
}
