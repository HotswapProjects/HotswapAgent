package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.Map;

public class ResetAnnotationCache {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ResetAnnotationCache.class);
    /**
     * Reset Spring annotation scanner.
     * @since 5.x
     */
    public static void resetAnnotationScanner(DefaultListableBeanFactory defaultListableBeanFactory) {
        Map<Method, String> declaredAnnotationCache = (Map<Method, String>) ReflectionHelper.getNoException(null,
                "org.springframework.core.annotation.AnnotationsScanner",
                defaultListableBeanFactory.getClass().getClassLoader(), "declaredAnnotationCache");
        if (declaredAnnotationCache != null) {
            LOGGER.trace("Cache cleared: AnnotationsScanner.beanNameCache");
            declaredAnnotationCache.clear();
        }

        Map<Method, String> baseTypeMethodsCache = (Map<Method, String>) ReflectionHelper.getNoException(null,
                "org.springframework.core.annotation.AnnotationsScanner",
                defaultListableBeanFactory.getClass().getClassLoader(), "baseTypeMethodsCache");
        if (baseTypeMethodsCache != null) {
            LOGGER.trace("Cache cleared: BeanAnnotationHelper.baseTypeMethodsCache");
            baseTypeMethodsCache.clear();
        }
    }
}
