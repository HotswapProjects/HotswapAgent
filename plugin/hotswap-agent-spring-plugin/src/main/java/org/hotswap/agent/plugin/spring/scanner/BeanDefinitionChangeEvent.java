package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class BeanDefinitionChangeEvent extends SpringEvent<BeanDefinitionHolder> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public BeanDefinitionChangeEvent(BeanDefinitionHolder source, ConfigurableListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}