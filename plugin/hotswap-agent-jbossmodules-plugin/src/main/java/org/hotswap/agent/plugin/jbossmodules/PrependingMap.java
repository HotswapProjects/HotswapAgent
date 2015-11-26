package org.hotswap.agent.plugin.jbossmodules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("rawtypes")
public class PrependingMap implements Map {

    private Map masterMap;
    private Object prependList;

    public PrependingMap(Map masterMap, Object prependList) {
        this.masterMap = masterMap;
        this.prependList = prependList;
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
        if (prependList != null) {
           // TODO : is there any situation when there is no path registered in the ModuleClassLoader
           // and prepending loader should be returned?
//            if (list == null) {
//                List result = new ArrayList();
//                result.addAll((List) prependList);
//                return result;
//            }
            if (list instanceof List){
                List result = new ArrayList<>((List) list);
                result.addAll(0, (List) prependList);
                return result;
            }
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
