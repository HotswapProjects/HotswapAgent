package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class AutowiredAnnotationProcessor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AutowiredAnnotationProcessor.class);

    public static void processSingletonBeanInjection(DefaultListableBeanFactory beanFactory) {
        try {
            Map<String, AutowiredAnnotationBeanPostProcessor> postProcessors = beanFactory.getBeansOfType(
                AutowiredAnnotationBeanPostProcessor.class);
            if (postProcessors.isEmpty()) {
                LOGGER.debug("AutowiredAnnotationProcessor not exist");
                return;
            }
            AutowiredAnnotationBeanPostProcessor postProcessor = postProcessors.values().iterator().next();
            boolean postProcessPropertyValuesNotExists = false;
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    if (postProcessPropertyValuesNotExists) {
                        // spring 6.x
                        postProcessor.postProcessProperties(null, object, beanName);
                        continue;
                    }
                    try {
                        // from spring 3.2.x to 5.x
                        postProcessor.postProcessPropertyValues(null, null, object, beanName);
                    } catch (NoSuchMethodError e) {
                        // spring 6.x
                        postProcessor.postProcessProperties(null, object, beanName);
                        postProcessPropertyValuesNotExists = true;
                    }
                }
            }
        } catch (Throwable e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AutowiredAnnotationProcessor maybe not exist", e);
            } else {
                LOGGER.warning("AutowiredAnnotationProcessor maybe not exist : " + e.getMessage());
            }
        }
    }

}
