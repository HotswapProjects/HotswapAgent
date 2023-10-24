package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.EventObject;

/**
 * Spring event.
 * It is used to notify the plugin that a bean definition has changed.
 * Spring boot and Spring should send some events each other, the event and listener are used to satisfy this requirement.
 *
 * @param <T> the event type
 */
public abstract class SpringEvent<T> extends EventObject {

    private ConfigurableListableBeanFactory beanFactory;

    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public SpringEvent(T source, ConfigurableListableBeanFactory beanFactory) {
        super(source);
        this.beanFactory = beanFactory;
    }

    public T getSource() {
        return (T) super.getSource();
    }

    public ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }
}
