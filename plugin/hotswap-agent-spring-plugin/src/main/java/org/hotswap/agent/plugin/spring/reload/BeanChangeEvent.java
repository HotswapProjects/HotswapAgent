package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class BeanChangeEvent extends SpringEvent<String[]> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source      the object on which the Event initially occurred
     * @param beanFactory
     * @throws IllegalArgumentException if source is null
     */
    public BeanChangeEvent(String[] source, ConfigurableListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}
