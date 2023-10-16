package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class BeanChangeEvent extends SpringEvent<String[]> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source      the object on which the Event initially occurred
     * @param beanFactory
     * @throws IllegalArgumentException if source is null
     */
    public BeanChangeEvent(String[] source, DefaultListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}
