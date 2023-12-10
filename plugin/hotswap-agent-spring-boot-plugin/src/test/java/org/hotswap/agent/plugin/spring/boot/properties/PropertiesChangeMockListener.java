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

    public boolean shouldSkip(SpringEvent<?> event) {
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
