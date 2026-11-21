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
package org.hotswap.agent.plugin.spring.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.plugin.spring.getbean.SpringHotswapAgentProxy;

/**
 * Lookup in the map of the resources which a Spring TransactionSynchronizationManager has bound, e.g. to a thread.
 * <p>
 * The key of a resource is e.g. the data source or the entity manager factory. A reload may change the hash code of
 * such a key while the resource is bound:
 * <ul>
 * <li>the proxy of the agent around a CGLIB proxy bean takes it from the bean, which it gets again after a
 * reload,</li>
 * <li>a JDK proxy takes it from its invocation handler, which a reload may replace, see
 * {@link #hashCodeChanged(Object)}.</li>
 * </ul>
 * The map does not find the key anymore then, although it is the same object: the commit of the transaction fails
 * with "No value for key [...] bound to thread", and the connection of the transaction is not used. So a key that is
 * not found is looked up again among the bound keys which the agent may have changed, by its identity. A JDK proxy
 * is also found by a key that is equal to it, i.e. by the new proxy of its bean. Other keys are found by their hash
 * code only, as before: a proxy may change it on purpose.
 * <p>
 * Called by the code which TransactionSynchronizationManagerTransformer puts into the manager.
 */
public class TransactionResourceMap {

    // Proxies whose hash code a reload may have changed, by their identity hash code. Read without a lock by every
    // lookup of a resource that is not found.
    private static final Map<Integer, ChangedProxy[]> changedProxies = new ConcurrentHashMap<>();
    // also the lock of the writers
    private static final ReferenceQueue<Object> collectedProxies = new ReferenceQueue<>();
    // no lookup among the changed proxies until a reload has changed one
    private static volatile boolean anyChangedProxy;

    // The iterator of HashMap removes an entry by the hash code the map has stored for it. On Java 8 it looks the
    // entry up by the current hash code and equals() of its key: it removes nothing, or the entry of an equal key.
    private static final boolean ITERATOR_REMOVES_CHANGED_KEY = iteratorRemovesChangedKey();

    private static final class ChangedProxy extends WeakReference<Object> {
        final Integer identity;

        ChangedProxy(Object proxy, Integer identity) {
            super(proxy, collectedProxies);
            this.identity = identity;
        }
    }

    private static boolean iteratorRemovesChangedKey() {
        final int[] hashCode = {1};
        Object key = new Object() {
            @Override
            public int hashCode() {
                return hashCode[0];
            }
        };
        Map<Object, Object> map = new HashMap<>();
        map.put(key, key);
        hashCode[0] = 2;
        Iterator<Object> it = map.keySet().iterator();
        it.next();
        it.remove();
        return map.isEmpty();
    }

    /**
     * Tells that the hash code of a proxy may change, as a reload replaces its invocation handler. It must be called
     * before the handler is replaced: a transaction of another thread may look its resource up right after it.
     */
    public static void hashCodeChanged(Object proxy) {
        synchronized (collectedProxies) {
            removeCollected();
            if (!isChangedProxy(proxy)) {
                Integer identity = System.identityHashCode(proxy);
                ChangedProxy[] proxies = changedProxies.get(identity);
                proxies = proxies == null ? new ChangedProxy[1] : Arrays.copyOf(proxies, proxies.length + 1);
                proxies[proxies.length - 1] = new ChangedProxy(proxy, identity);
                changedProxies.put(identity, proxies);
            }
            anyChangedProxy = true;
        }
    }

    // removes the proxies collected meanwhile, called by the writers only
    private static void removeCollected() {
        Reference<?> collected;
        while ((collected = collectedProxies.poll()) != null) {
            Integer identity = ((ChangedProxy) collected).identity;
            ChangedProxy[] proxies = changedProxies.get(identity);
            if (proxies != null) {
                List<ChangedProxy> others = new ArrayList<>(proxies.length);
                for (ChangedProxy proxy : proxies) {
                    if (proxy != collected) {
                        others.add(proxy);
                    }
                }
                if (others.isEmpty()) {
                    changedProxies.remove(identity);
                } else {
                    changedProxies.put(identity, others.toArray(new ChangedProxy[0]));
                }
            }
        }
        anyChangedProxy = !changedProxies.isEmpty();
    }

    private static boolean isChangedProxy(Object key) {
        ChangedProxy[] proxies = changedProxies.get(System.identityHashCode(key));
        if (proxies != null) {
            for (ChangedProxy proxy : proxies) {
                if (proxy.get() == key) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isChangedJdkProxy(Object key) {
        return anyChangedProxy && isChangedProxy(key);
    }

    /**
     * Replaces Map.get(key).
     */
    public static Object get(Map<Object, Object> resources, Object key) {
        Object value = resources.get(key);
        return value != null ? value : lookup(resources, key, false);
    }

    /**
     * Replaces Map.remove(key).
     */
    public static Object remove(Map<Object, Object> resources, Object key) {
        Object value = resources.remove(key);
        return value != null ? value : lookup(resources, key, true);
    }

    private static Object lookup(Map<Object, Object> resources, Object key, boolean remove) {
        if (key == null) {
            return null;
        }
        Object boundKey = findBoundKey(resources, key);
        if (boundKey == null) {
            return null;
        }
        Object value = null;
        for (Map.Entry<Object, Object> entry : resources.entrySet()) {
            if (entry.getKey() == boundKey) {
                value = entry.getValue();
                break;
            }
        }
        if (remove && !removeEntry(resources, boundKey, ITERATOR_REMOVES_CHANGED_KEY)) {
            // It is still bound, so the caller must not take the resource as unbound.
            return null;
        }
        return value;
    }

    /**
     * Finds the bound key which the map does not find by the hash code of the given key anymore.
     */
    private static Object findBoundKey(Map<Object, Object> resources, Object key) {
        // The key itself. Nothing is called on the keys here.
        List<Object> changedJdkProxies = null;
        for (Object boundKey : resources.keySet()) {
            if (boundKey == key) {
                return key instanceof SpringHotswapAgentProxy || isChangedJdkProxy(key) ? key : null;
            }
            if (isChangedJdkProxy(boundKey)) {
                if (changedJdkProxies == null) {
                    changedJdkProxies = new ArrayList<>(1);
                }
                changedJdkProxies.add(boundKey);
            }
        }
        // The new proxy of a bean, which is equal to the old one that got its invocation handler. equals() may get
        // a bean and so bind a resource, so it is not called while the map is read. It is never called for the proxy
        // of the agent: it would get its bean from the bean factory, and it is equal to no other key anyway.
        if (changedJdkProxies != null && !(key instanceof SpringHotswapAgentProxy)) {
            for (Object boundKey : changedJdkProxies) {
                if (isEqual(key, boundKey) || isEqual(boundKey, key)) {
                    return boundKey;
                }
            }
        }
        return null;
    }

    /**
     * Removes the entry of a key that the map does not find by its hash code.
     *
     * @param byIterator the iterator of the map removes such an entry, see ITERATOR_REMOVES_CHANGED_KEY
     * @return false if the entry was not removed, the map is unchanged then
     */
    static boolean removeEntry(Map<Object, Object> resources, Object boundKey, boolean byIterator) {
        if (byIterator) {
            for (Iterator<Object> it = resources.keySet().iterator(); it.hasNext(); ) {
                if (it.next() == boundKey) {
                    it.remove();
                    return true;
                }
            }
            return false;
        }
        // The map is filled again without the entry. The other keys get their hash code first: if one of them
        // fails (the proxy of the agent of a bean that is gone), or two of them are equal now, nothing is removed.
        int size = resources.size();
        Map<Object, Object> others = new HashMap<>();
        boolean found = false;
        try {
            for (Map.Entry<Object, Object> entry : resources.entrySet()) {
                if (entry.getKey() == boundKey) {
                    found = true;
                } else {
                    others.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            return false;
        }
        if (!found || others.size() != size - 1) {
            return false;
        }
        resources.clear();
        resources.putAll(others);
        return true;
    }

    private static boolean isEqual(Object key, Object otherKey) {
        try {
            return key.equals(otherKey);
        } catch (Exception e) {
            // equals() of a proxy calls its bean, which may be gone
            return false;
        }
    }
}
