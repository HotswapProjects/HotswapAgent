package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Map;

public class AutowiredAnnotationProcessor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AutowiredAnnotationProcessor.class);

    public static void processSingletonBeanInjection(DefaultListableBeanFactory beanFactory) {
        try {
            Map<String, AutowiredAnnotationBeanPostProcessor> postProcessors = beanFactory.getBeansOfType(AutowiredAnnotationBeanPostProcessor.class);
            if (postProcessors == null || postProcessors.isEmpty()) {
                LOGGER.debug("AutowiredAnnotationProcessor not exist");
                return;
            }
            AutowiredAnnotationBeanPostProcessor postProcessor = postProcessors.values().iterator().next();
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    postProcessor.postProcessPropertyValues(null, null, object, beanName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("AutowiredAnnotationProcessor maybe not exist", e);
        }
    }
}
