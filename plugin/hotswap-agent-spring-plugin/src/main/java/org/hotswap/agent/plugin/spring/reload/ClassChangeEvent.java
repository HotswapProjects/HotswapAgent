package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class ClassChangeEvent extends SpringEvent<Class> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public ClassChangeEvent(Class<?> source, ConfigurableListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}
