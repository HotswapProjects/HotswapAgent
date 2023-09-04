package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.EventObject;

/**
 * Spring event.
 *
 * @param <T> the event type
 */
public abstract class SpringEvent<T> extends EventObject {

    private DefaultListableBeanFactory beanFactory;

    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public SpringEvent(T source, DefaultListableBeanFactory beanFactory) {
        super(source);
        this.beanFactory = beanFactory;
    }

    public T getSource() {
        return (T) super.getSource();
    }

    public DefaultListableBeanFactory getBeanFactory() {
        return beanFactory;
    }
}
