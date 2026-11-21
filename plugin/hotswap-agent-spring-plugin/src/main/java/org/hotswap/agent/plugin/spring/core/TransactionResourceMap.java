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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    // proxies whose hash code a reload may have changed, by their identity hash code
    private static final Map<Integer, List<ChangedProxy>> changedProxies = new HashMap<>();
    private static final ReferenceQueue<Object> collectedProxies = new ReferenceQueue<>();
    // no lookup among the changed proxies, and no lock, until a reload has changed one
    private static volatile boolean anyChangedProxy;

    private static final class ChangedProxy extends WeakReference<Object> {
        final Integer identity;

        ChangedProxy(Object proxy, Integer identity) {
            super(proxy, collectedProxies);
            this.identity = identity;
        }
    }

    /**
     * Tells that the hash code of a proxy may change, as a reload replaces its invocation handler. It must be called
     * before the handler is replaced: a transaction of another thread may look its resource up right after it.
     */
    public static void hashCodeChanged(Object proxy) {
        synchronized (changedProxies) {
            removeCollected();
            if (!isChangedProxy(proxy)) {
                Integer identity = System.identityHashCode(proxy);
                List<ChangedProxy> proxies = changedProxies.get(identity);
                if (proxies == null) {
                    proxies = new ArrayList<>(1);
                    changedProxies.put(identity, proxies);
                }
                proxies.add(new ChangedProxy(proxy, identity));
            }
            anyChangedProxy = true;
        }
    }

    // removes the proxies collected meanwhile
    private static void removeCollected() {
        Reference<?> collected;
        while ((collected = collectedProxies.poll()) != null) {
            List<ChangedProxy> proxies = changedProxies.get(((ChangedProxy) collected).identity);
            if (proxies != null) {
                proxies.remove(collected);
                if (proxies.isEmpty()) {
                    changedProxies.remove(((ChangedProxy) collected).identity);
                }
            }
        }
        anyChangedProxy = !changedProxies.isEmpty();
    }

    private static boolean isChangedProxy(Object key) {
        synchronized (changedProxies) {
            removeCollected();
            List<ChangedProxy> proxies = changedProxies.get(System.identityHashCode(key));
            if (proxies != null) {
                for (ChangedProxy proxy : proxies) {
                    if (proxy.get() == key) {
                        return true;
                    }
                }
            }
            return false;
        }
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
        if (remove) {
            removeEntry(resources, boundKey);
        }
        return value;
    }

    /**
     * Finds the bound key which the map does not find by the hash code of the given key anymore.
     */
    private static Object findBoundKey(Map<Object, Object> resources, Object key) {
        // The key itself. Nothing is called on the keys here: equals() of the proxy of the agent would get its bean
        // from the bean factory, and that proxy is equal to no other key anyway.
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
        // a bean and so bind a resource, so it is not called while the map is read.
        if (changedJdkProxies != null) {
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
     */
    private static void removeEntry(Map<Object, Object> resources, Object boundKey) {
        int size = resources.size();
        for (Iterator<Object> it = resources.keySet().iterator(); it.hasNext(); ) {
            if (it.next() == boundKey) {
                it.remove();
                break;
            }
        }
        if (resources.size() < size) {
            return;
        }
        // The iterator of HashMap on Java 8 looks the entry up by the current hash code of its key and removes
        // nothing. The map is filled again without the entry. The other keys get their hash code first: if one of
        // them fails (the proxy of the agent of a bean that is gone), the map stays as it is.
        Map<Object, Object> others = new HashMap<>();
        try {
            for (Map.Entry<Object, Object> entry : resources.entrySet()) {
                if (entry.getKey() != boundKey) {
                    others.put(entry.getKey(), entry.getValue());
                }
            }
            if (others.size() != size - 1) {
                // two of the other keys are equal now
                return;
            }
        } catch (Exception e) {
            return;
        }
        resources.clear();
        resources.putAll(others);
    }

    private static boolean isEqual(Object key, Object otherKey) {
        try {
            return key.equals(otherKey);
        } catch (Exception e) {
            // equals() of a proxy calls its bean, which may be gone. A CGLIB proxy also throws exceptions which
            // equals() does not declare.
            return false;
        }
    }
}
