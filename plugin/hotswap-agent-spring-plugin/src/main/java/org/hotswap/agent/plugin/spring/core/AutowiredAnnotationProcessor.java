package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class AutowiredAnnotationProcessor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AutowiredAnnotationProcessor.class);

    public static void processSingletonBeanInjection(DefaultListableBeanFactory beanFactory) {
        try {
            AutowiredAnnotationBeanPostProcessor postProcessor = beanFactory.getBean(AutowiredAnnotationBeanPostProcessor.class);
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    postProcessor.postProcessProperties(null, object, beanName);
                }
            }
        } catch (Exception e) {
            LOGGER.info("AutowiredAnnotationProcessor maybe not exist", e);
        }
    }
}
