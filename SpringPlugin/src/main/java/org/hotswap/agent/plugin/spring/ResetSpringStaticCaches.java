package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.Property;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Reset various Spring static caches. It is safe to run multiple times,
 * basically every time any configuration is changed.
 *
 * @author Jiri Bubnik
 */
public class ResetSpringStaticCaches {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ResetSpringStaticCaches.class);

    /**
     * Reset all caches.
     */
    public static void reset() {
        resetTypeVariableCache();
        resetAnnotationUtilsCache();
        resetPropetyCache();
        CachedIntrospectionResults.clearClassLoader(ResetSpringStaticCaches.class.getClassLoader());
    }

    private static void resetTypeVariableCache() {
        try {
            Field field = GenericTypeResolver.class.getDeclaredField("typeVariableCache");
            field.setAccessible(true);
            // noinspection unchecked
            Map<Class, Map> typeVariableCache = (Map<Class, Map>) field.get(null);
            typeVariableCache.clear();
            LOGGER.debug("Cache cleared: GenericTypeResolver.typeVariableCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear GenericTypeResolver.typeVariableCache", e);
        }
    }

    private static void resetAnnotationUtilsCache() {
        try {
            Field field = AnnotationUtils.class.getDeclaredField("annotatedInterfaceCache");
            field.setAccessible(true);
            // noinspection unchecked
            Map<Class, Boolean> annotatedInterfaceCache = (Map<Class, Boolean>) field.get(null);
            annotatedInterfaceCache.clear();
            LOGGER.debug("Cache cleared: AnnotationUtils.annotatedInterfaceCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear AnnotationUtils.annotatedInterfaceCache", e);
        }
    }

    private static void resetPropetyCache() {
        try {
            Field field = Property.class.getDeclaredField("annotationCache");
            field.setAccessible(true);
            // noinspection unchecked
            Map<Class, Boolean> annotationCache = (Map<Class, Boolean>) field.get(null);
            annotationCache.clear();
            LOGGER.debug("Cache cleared: Property.annotationCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear Property.annotationCache", e);
        }
    }
}
