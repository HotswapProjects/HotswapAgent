package org.hotswap.agent.plugin.spring.listener;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.reload.SpringBeanReload;

import java.util.ArrayList;
import java.util.List;

public class SpringEventSource {

    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringEventSource.class);
    public static final SpringEventSource INSTANCE = new SpringEventSource();

    private SpringEventSource() {
    }

    private List<SpringListener> listeners = new ArrayList<SpringListener>();

    public void addListener(SpringListener listener) {
        listeners.add(listener);
    }

    public void fireEvent(SpringEvent event) {
        for (SpringListener listener : listeners) {
            if (listener.isFilterBeanFactory() && listener.beanFactory() != null &&
                    listener.beanFactory() != event.getBeanFactory()) {
                continue;
            }
            try {
                listener.onEvent(event);
            } catch (Throwable e) {
                LOGGER.warning("SpringListener onEvent error", e);
            }
        }
    }
}
