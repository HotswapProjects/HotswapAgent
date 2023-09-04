package org.hotswap.agent.plugin.spring.listener;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;

public class PropertiesChangeEvent extends SpringEvent<List<PropertiesChangeEvent.PropertyChangeItem>> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public PropertiesChangeEvent(List<PropertyChangeItem> source, DefaultListableBeanFactory beanFactory) {
        super(source, beanFactory);
    }

    public static class PropertyChangeItem {
        public PropertyChangeItem(String key, String oldValue, String newValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        private String key;
        private String oldValue;
        private String newValue;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getOldValue() {
            return oldValue;
        }

        public void setOldValue(String oldValue) {
            this.oldValue = oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public void setNewValue(String newValue) {
            this.newValue = newValue;
        }
    }
}
