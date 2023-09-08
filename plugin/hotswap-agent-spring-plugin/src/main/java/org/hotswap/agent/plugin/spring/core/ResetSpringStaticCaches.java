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
import java.util.Map;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Reset various Spring static caches. It is safe to run multiple times,
 * basically every time any configuration is changed.
 *
 * @author Jiri Bubnik
 */
public class ResetSpringStaticCaches {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ResetSpringStaticCaches.class);

    /**
     * Spring bean by type cache.
     *
     * Cache names change between versions, call via reflection and ignore errors.
     */
    public static void resetBeanNamesByType(DefaultListableBeanFactory defaultListableBeanFactory) {
        try {
            Field field = DefaultListableBeanFactory.class.getDeclaredField("singletonBeanNamesByType");
            field.setAccessible(true);
            // noinspection unchecked
            Map singletonBeanNamesByType = (Map) field.get(defaultListableBeanFactory);
            singletonBeanNamesByType.clear();
        } catch (Exception e) {
            LOGGER.trace("Unable to clear DefaultListableBeanFactory.singletonBeanNamesByType cache (is Ok for pre 3.1.2 Spring version)", e);
        }

        try {
            Field field = DefaultListableBeanFactory.class.getDeclaredField("allBeanNamesByType");
            field.setAccessible(true);
            // noinspection unchecked
            Map allBeanNamesByType = (Map) field.get(defaultListableBeanFactory);
            allBeanNamesByType.clear();
        } catch (Exception e) {
            LOGGER.trace("Unable to clear allBeanNamesByType cache (is Ok for pre 3.2 Spring version)");
        }

        try {
            Field field = DefaultListableBeanFactory.class.getDeclaredField("nonSingletonBeanNamesByType");
            field.setAccessible(true);
            // noinspection unchecked
            Map nonSingletonBeanNamesByType = (Map) field.get(defaultListableBeanFactory);
            nonSingletonBeanNamesByType.clear();
        } catch (Exception e) {
            LOGGER.debug("Unable to clear nonSingletonBeanNamesByType cache (is Ok for pre 3.2 Spring version)");
        }

    }

    /**
     * Reset all caches.
     */
    public static void reset() {
        resetTypeVariableCache();
        resetAnnotationUtilsCache();
        resetReflectionUtilsCache();
        resetResolvableTypeCache();
        resetPropertyCache();
        CachedIntrospectionResults.clearClassLoader(ResetSpringStaticCaches.class.getClassLoader());
    }

    private static void resetResolvableTypeCache() {
        ReflectionHelper.invokeNoException(null, "org.springframework.core.ResolvableType",
                ResetSpringStaticCaches.class.getClassLoader(), "clearCache", new Class<?>[] {});
    }

    private static void resetTypeVariableCache() {
        try {
            Field field = GenericTypeResolver.class.getDeclaredField("typeVariableCache");
            field.setAccessible(true);
            // noinspection unchecked
            Map<Class, Map> typeVariableCache = (Map<Class, Map>) field.get(null);
            typeVariableCache.clear();
            LOGGER.trace("Cache cleared: GenericTypeResolver.typeVariableCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear GenericTypeResolver.typeVariableCache", e);
        }
    }

    private static void resetReflectionUtilsCache() {
        ReflectionHelper.invokeNoException(null, "org.springframework.util.ReflectionUtils",
                ResetSpringStaticCaches.class.getClassLoader(), "clearCache", new Class<?>[] {});

        Map declaredMethodsCache = (Map) ReflectionHelper.getNoException(null, ReflectionUtils.class,
                "declaredMethodsCache");
        if (declaredMethodsCache != null) {
            declaredMethodsCache.clear();
            LOGGER.trace("Cache cleared: ReflectionUtils.declaredMethodsCache");
        } else {
            LOGGER.trace("Cache NOT cleared: ReflectionUtils.declaredMethodsCache not exists");
        }
    }

    private static void resetAnnotationUtilsCache() {
        ReflectionHelper.invokeNoException(null, "org.springframework.core.annotation.AnnotationUtils",
                ResetSpringStaticCaches.class.getClassLoader(), "clearCache", new Class<?>[] {});

        Map annotatedInterfaceCache = (Map) ReflectionHelper.getNoException(null, AnnotationUtils.class,
                "annotatedInterfaceCache");
        if (annotatedInterfaceCache != null) {
            annotatedInterfaceCache.clear();
            LOGGER.trace("Cache cleared: AnnotationUtils.annotatedInterfaceCache");
        } else {
            LOGGER.trace("Cache NOT cleared: AnnotationUtils.annotatedInterfaceCache not exists in target Spring verion (pre 3.1.x)");
        }

        Map findAnnotationCache = (Map) ReflectionHelper.getNoException(null, AnnotationUtils.class, "findAnnotationCache");
        if (findAnnotationCache != null) {
            findAnnotationCache.clear();
            LOGGER.trace("Cache cleared: AnnotationUtils.findAnnotationCache");
        } else {
            LOGGER.trace("Cache NOT cleared: AnnotationUtils.findAnnotationCache not exists in target Spring version (pre 4.1)");
        }

    }

    private static void resetPropertyCache() {
        try {
            ClassLoader classLoader = ResetSpringStaticCaches.class.getClassLoader();
            Map annotationCache = (Map) ReflectionHelper.get(null,
                    classLoader.loadClass("org.springframework.core.convert.Property"), "annotationCache");
            annotationCache.clear();
            LOGGER.trace("Cache cleared: Property.annotationCache");
        } catch (Exception e) {
            LOGGER.trace("Unable to clear Property.annotationCache (ok before Spring 3.2.x)", e);
        }
    }
}
