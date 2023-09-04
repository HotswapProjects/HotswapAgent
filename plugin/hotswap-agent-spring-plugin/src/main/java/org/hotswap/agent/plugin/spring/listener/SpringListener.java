package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.EventListener;

/**
 * Spring event.
 *
 * @param <E> the event type
 */
public interface SpringListener<E extends SpringEvent> extends EventListener {

    DefaultListableBeanFactory beanFactory();

    /**
     * Handle the event.
     *
     * @param event the event to respond to
     */
    void onEvent(E event);
}
