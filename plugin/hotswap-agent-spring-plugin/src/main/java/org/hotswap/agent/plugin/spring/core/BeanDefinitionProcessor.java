package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class BeanDefinitionProcessor {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(BeanDefinitionProcessor.class);

    public static void registerBeanDefinition(DefaultListableBeanFactory defaultListableBeanFactory, String beanName, BeanDefinition beanDefinition) {
        LOGGER.debug("register bean '{}' into '{}' with definition '{}'", beanName, ObjectUtils.identityToString(defaultListableBeanFactory),
                beanDefinition);
        org.hotswap.agent.plugin.spring.xml.XmlBeanDefinitionScannerAgent.registerBean(beanName, beanDefinition);
    }

    public static void removeBeanDefinition(DefaultListableBeanFactory defaultListableBeanFactory, String beanName) {
        LOGGER.debug("remove bean '{}' from '{}'", beanName, ObjectUtils.identityToString(defaultListableBeanFactory));
    }
}
