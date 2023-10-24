package org.hotswap.agent.plugin.spring.boot.properties;

import org.hotswap.agent.plugin.spring.files.PropertiesChangeEvent;
import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.hotswap.agent.plugin.spring.listener.SpringListener;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.HashMap;
import java.util.Map;

public class PropertiesChangeMockListener implements SpringListener<SpringEvent<?>> {

    Map<String, String> oldValueContainer = new HashMap<>();
    Map<String, String> newValueContainer = new HashMap<>();

    public boolean isFilterBeanFactory() {
        return false;
    }

    @Override
    public DefaultListableBeanFactory beanFactory() {
        return null;
    }

    @Override
    public void onEvent(SpringEvent<?> event) {
        if (event instanceof PropertiesChangeEvent) {
            PropertiesChangeEvent propertiesChangeEvent = (PropertiesChangeEvent) event;
            for (PropertiesChangeEvent.PropertyChangeItem propertyChangeItem : propertiesChangeEvent.getSource()) {
                oldValueContainer.put(propertyChangeItem.getKey(), propertyChangeItem.getOldValue());
                newValueContainer.put(propertyChangeItem.getKey(), propertyChangeItem.getNewValue());
            }
        }
    }

    public Map<String, String> oldValueMap() {
        return oldValueContainer;
    }

    public Map<String, String> newValueMap() {
        return newValueContainer;
    }

    public void clear() {
        oldValueContainer.clear();
        newValueContainer.clear();
    }
}
