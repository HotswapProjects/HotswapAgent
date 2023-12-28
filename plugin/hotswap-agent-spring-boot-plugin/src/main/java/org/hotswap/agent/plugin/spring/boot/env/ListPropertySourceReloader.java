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
package org.hotswap.agent.plugin.spring.boot.env;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ListPropertySourceReloader: reload List<Map<String, T>>.
 * @see PropertySourceLoader#load(String, Resource)
 *
 * @param <T>
 */
public class ListPropertySourceReloader<T> implements HotswapSpringPropertiesReloader<List<Map<String, T>>> {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ListPropertySourceReloader.class);

    protected final String name;
    protected final Resource resource;
    protected List<Map<String, T>> hotswapMapList;

    public ListPropertySourceReloader(String name, Resource resource) {
        this.name = name;
        this.resource = resource;
    }

    Map<String, T> createMap(Map map) {
        return new HotswapSpringReloadMap<>(map);
    }

    void updateItemValue(Map hotswapMap, Map newHotswapMap) {
        if (hotswapMap instanceof HotswapSpringReloadMap) {
            ((HotswapSpringReloadMap) hotswapMap).update(newHotswapMap);
        }
    }

    @Override
    public void update(List<Map<String, T>> newValue) {
        if (newValue == null || newValue.isEmpty()) {
            hotswapMapList = (newValue == null ? Collections.emptyList() : newValue);
            return;
        }
        if (hotswapMapList == null) {
            synchronized (this) {
                if (hotswapMapList == null) {
                    hotswapMapList = new ArrayList<>(newValue.size());
                    for (Map<String, ?> map : newValue) {
                        hotswapMapList.add(createMap(map));
                    }
                    return;
                }
            }
        }
        for (int i = 0; i < hotswapMapList.size(); i++) {
            Map<String, ?> hotswapMap = hotswapMapList.get(i);
            Map<String, ?> newHotswapMap = newValue.get(i);
            updateItemValue(hotswapMap, newHotswapMap);
        }
    }

    @Override
    public List<Map<String, T>> get() {
        return hotswapMapList;
    }
}
