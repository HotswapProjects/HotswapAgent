/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.jbossmodules;

import java.util.*;

/**
 * MapOrDefault
 */
@SuppressWarnings("rawtypes")
public class MapOrDefault implements Map {

    private Map masterMap;
    private Object defaultValue;

    public MapOrDefault(Map masterMap, Object defaultValue) {
        this.masterMap = masterMap;
        this.defaultValue = defaultValue;
    }

    @Override
    public void clear() {
       masterMap.clear();
    }

    @Override
    public boolean containsKey(Object paramObject) {
        return masterMap.containsKey(paramObject);
    }

    @Override
    public boolean containsValue(Object paramObject) {
        return masterMap.containsValue(paramObject);
    }

    @Override
    public Set entrySet() {
        return masterMap.entrySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object get(Object paramObject) {
        Object list = masterMap.get(paramObject);
        if (list == null) {
            return defaultValue;
        }
        return list;
    }

    @Override
    public boolean isEmpty() {
        return masterMap.isEmpty();
    }

    @Override
    public Set keySet() {
        return masterMap.keySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object put(Object paramK, Object paramV) {
        return masterMap.put(paramK, paramV);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putAll(Map paramMap) {
        masterMap.putAll(paramMap);

    }

    @Override
    public Object remove(Object paramObject) {
        return masterMap.remove(paramObject);
    }

    @Override
    public int size() {
        return masterMap.size();
    }

    @Override
    public Collection values() {
        return masterMap.values();
    }

}
