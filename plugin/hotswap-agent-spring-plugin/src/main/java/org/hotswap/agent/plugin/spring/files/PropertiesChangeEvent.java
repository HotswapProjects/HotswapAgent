/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.files;

import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;

/**
 * Properties change event, it will be used to notify spring boot to reload the related Bean.
 */
public class PropertiesChangeEvent extends SpringEvent<List<PropertiesChangeEvent.PropertyChangeItem>> {
    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @throws IllegalArgumentException if source is null
     */
    public PropertiesChangeEvent(List<PropertyChangeItem> source, ConfigurableListableBeanFactory beanFactory) {
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
