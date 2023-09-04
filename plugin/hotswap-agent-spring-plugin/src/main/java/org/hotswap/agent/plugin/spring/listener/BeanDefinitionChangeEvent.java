package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class BeanDefinitionChangeEvent extends SpringEvent<Class> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public BeanDefinitionChangeEvent(Class source, DefaultListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}