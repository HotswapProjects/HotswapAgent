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

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Spring Bean post processors contain various caches for performance reasons. Clear the caches on reload.
 *
 * @author Jiri Bubnik
 */
public class ResetBeanPostProcessorCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanPostProcessorCaches.class);

    private static Class<?> getReflectionUtilsClassOrNull() {
        try {
            //This is probably a bad idea as Class.forName has lots of issues but this was easiest for now.
            return Class.forName("org.springframework.util.ReflectionUtils");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("Spring 4.1.x or below - ReflectionUtils class not found");
            return null;
        }
    }

    /**
     * Reset all post processors associated with a bean factory.
     *
     * @param beanFactory beanFactory to use
     */
    public static void reset(DefaultListableBeanFactory beanFactory) {
        Class<?> c = getReflectionUtilsClassOrNull();
        if (c != null) {
            try {
                Method m = c.getDeclaredMethod("clearCache");
                m.invoke(null);
                LOGGER.trace("Cleared Spring 4.2+ internal method/field cache.");
            } catch (Exception version42Failed) {
                LOGGER.debug("Failed to clear internal method/field cache, it's normal with spring 4.1.x or lower", version42Failed);
                // spring 4.0.x, 4.1.x without clearCache method, clear manually
                Object declaredMethodsCache = ReflectionHelper.getNoException(null, c, "declaredMethodsCache");
                if (declaredMethodsCache != null) {
                    ((Map<?, ?>) declaredMethodsCache).clear();
                }

                Object declaredFieldsCache1 = ReflectionHelper.getNoException(null, c, "declaredFieldsCache");
                if (declaredFieldsCache1 != null) {
                    ((Map<?, ?>) declaredFieldsCache1).clear();
                }
            }
        }
        for (BeanPostProcessor bpp : beanFactory.getBeanPostProcessors()) {
            if (bpp instanceof AutowiredAnnotationBeanPostProcessor) {
                resetAutowiredAnnotationBeanPostProcessorCache((AutowiredAnnotationBeanPostProcessor) bpp);
            } else if (bpp instanceof CommonAnnotationBeanPostProcessor) {
                resetAnnotationBeanPostProcessorCache(bpp, CommonAnnotationBeanPostProcessor.class);
            } else if (bpp instanceof InitDestroyAnnotationBeanPostProcessor) {
                resetInitDestroyAnnotationBeanPostProcessorCache((InitDestroyAnnotationBeanPostProcessor) bpp);
            }
        }
    }

    public static void resetInitDestroyAnnotationBeanPostProcessorCache(InitDestroyAnnotationBeanPostProcessor bpp) {
        try {
            Field field = InitDestroyAnnotationBeanPostProcessor.class.getDeclaredField("lifecycleMetadataCache");
            field.setAccessible(true);
            Map lifecycleMetadataCache = (Map) field.get(bpp);
            lifecycleMetadataCache.clear();
            LOGGER.trace("Cache cleared: InitDestroyAnnotationBeanPostProcessor.lifecycleMetadataCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear InitDestroyAnnotationBeanPostProcessor.lifecycleMetadataCache", e);
        }
    }

    // @Autowired cache
    public static void resetAutowiredAnnotationBeanPostProcessorCache(AutowiredAnnotationBeanPostProcessor bpp) {
        try {
            Field field = AutowiredAnnotationBeanPostProcessor.class.getDeclaredField("candidateConstructorsCache");
            field.setAccessible(true);
            // noinspection unchecked
            Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = (Map<Class<?>, Constructor<?>[]>) field.get(bpp);
            candidateConstructorsCache.clear();
            LOGGER.trace("Cache cleared: AutowiredAnnotationBeanPostProcessor.candidateConstructorsCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear AutowiredAnnotationBeanPostProcessor.candidateConstructorsCache", e);
        }
        resetAnnotationBeanPostProcessorCache(bpp, AutowiredAnnotationBeanPostProcessor.class);
    }

    /**
     * deal injectionMetadataCache field of
     * @see org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
     * @see org.springframework.context.annotation.CommonAnnotationBeanPostProcessor
     */
    private static void resetAnnotationBeanPostProcessorCache(Object object, Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField("injectionMetadataCache");
            field.setAccessible(true);
            //noinspection unchecked
            Map<Class<?>, InjectionMetadata> injectionMetadataCache = (Map<Class<?>, InjectionMetadata>) field.get(object);
            injectionMetadataCache.clear();
            // noinspection unchecked
            LOGGER.trace("Cache cleared: AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor"
                + " injectionMetadataCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear "
                + "AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor injectionMetadataCache", e);
        }
    }
}
