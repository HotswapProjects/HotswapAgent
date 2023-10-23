package org.hotswap.agent.plugin.spring.listener;

import java.util.ArrayList;
import java.util.List;

public class SpringEventSource {

    public static final SpringEventSource INSTANCE = new SpringEventSource();

    private SpringEventSource() {
    }

    private List<SpringListener> listeners = new ArrayList<SpringListener>();

    public void addListener(SpringListener listener) {
        listeners.add(listener);
    }

    public void fireEvent(SpringEvent event) {
        for (SpringListener listener : listeners) {
            if (listener.beanFactory() != null && listener.beanFactory() != event.getBeanFactory()) {
                continue;
            }
            listener.onEvent(event);
        }
    }
}
