package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class ClassChangeEvent extends SpringEvent<Class> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public ClassChangeEvent(Class<?> source, DefaultListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }
}
