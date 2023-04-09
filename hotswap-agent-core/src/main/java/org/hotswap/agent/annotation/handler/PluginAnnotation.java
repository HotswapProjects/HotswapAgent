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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.versions.DeploymentInfo;
import org.hotswap.agent.versions.VersionMatchResult;
import org.hotswap.agent.versions.matcher.MethodMatcher;
import org.hotswap.agent.versions.matcher.PluginMatcher;

/**
 * DTO for resolved plugin annotation properties.
 * <p/>
 * Either method OR field is filled - never both (annotation on method or field).
 *
 * @author Jiri Bubnik
 */
public class PluginAnnotation<T extends Annotation> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginAnnotation.class);

    // the main plugin class
    Class<?> pluginClass;

    // target plugin object
    Object plugin;

    // the annotation to process
    T annotation;

    // annotation is on a field (and method property is empty)
    Field field;

    // annotation is on a method (and field property is empty)
    Method method;

    // plugin matcher
    final PluginMatcher pluginMatcher;

    // Method matcher
    final MethodMatcher methodMatcher;

    // plugin group (elresolver etc..)
    final String group;

    // Falback plugin - plugin is used if no other plugin in the group version matches
    final boolean fallback;

    public PluginAnnotation(Class<?> pluginClass, Object plugin, T annotation, Method method) {
        this.pluginClass = pluginClass;
        this.plugin = plugin;
        this.annotation = annotation;
        this.method = method;

        Plugin pluginAnnotation = pluginClass.getAnnotation(Plugin.class);
        this.group = (pluginAnnotation.group() != null && !pluginAnnotation.group().isEmpty()) ? pluginAnnotation.group() : null;
        this.fallback = pluginAnnotation.fallback();

        if(method != null && (Modifier.isStatic(method.getModifiers()))) {
            this.pluginMatcher = new PluginMatcher(pluginClass);
            this.methodMatcher= new MethodMatcher(method);
        } else {
            this.pluginMatcher = null;
            this.methodMatcher = null;
        }
    }

    public PluginAnnotation(Class<?> pluginClass, Object plugin, T annotation, Field field) {

        this.pluginClass = pluginClass;
        this.plugin = plugin;
        this.annotation = annotation;
        this.field = field;
        this.pluginMatcher = null;
        this.methodMatcher = null;
        this.fallback  = false;
        this.group = null;
    }

    /**
     * Return plugin class (the plugin to which this annotation belongs - not necessarily declaring class.
     */
    public Class<?> getPluginClass() {
        return pluginClass;
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

    public boolean shouldCheckVersion() {
        return //
        (this.plugin == null)//
                && //
                (//
                        (pluginMatcher != null && pluginMatcher.isApply()) //
                        || //
                        (methodMatcher != null && methodMatcher.isApply())//
                );//
    }

    /**
     * @return true, if plugin is fallback
     */
    public boolean isFallBack() {
       return fallback;
    }

    /**
     * @return the plugin group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Matches.
     *
     * @param deploymentInfo the deployment info
     * @return true, if successful
     */
    public boolean matches(DeploymentInfo deploymentInfo){
        if(deploymentInfo == null || (pluginMatcher == null && methodMatcher == null)) {
            LOGGER.debug("No matchers, apply");
            return true;
        }

        if(pluginMatcher != null && pluginMatcher.isApply()) {
            if(VersionMatchResult.REJECTED.equals(pluginMatcher.matches(deploymentInfo))){
                LOGGER.debug("Plugin matcher rejected");
                return false;
            }
        }
        if(methodMatcher != null && methodMatcher.isApply()) {
            if(VersionMatchResult.REJECTED.equals(methodMatcher.matches(deploymentInfo))){
                LOGGER.debug("Method matcher rejected");
                return false;
            }
        }
        return true;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginAnnotation<?> that = (PluginAnnotation<?>) o;

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
