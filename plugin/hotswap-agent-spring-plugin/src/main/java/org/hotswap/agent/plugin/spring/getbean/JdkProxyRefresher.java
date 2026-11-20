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
package org.hotswap.agent.plugin.spring.getbean;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.SimpleAliasRegistry;

/**
 * Refreshes the JDK dynamic proxies returned by a bean factory after a reload. A reference kept by the application
 * (or by a bean that is not recreated) to a JDK proxy bean would call the old bean. The proxy is not wrapped, code
 * of other libraries may cast its invocation handler (e.g. CXF ClientProxy.getClient(), see #327). Instead, after a
 * reload the invocation handler of the old proxy is replaced:
 * <ul>
 * <li>by the handler of the new proxy, for a singleton that exists after the reload,</li>
 * <li>by a {@link RefreshingInvocationHandler}, which gets the bean when the proxy is called, for other beans, e.g.
 * prototypes and lazy singletons not created yet.</li>
 * </ul>
 * A proxy of a prototype is refreshed only if its bean was destroyed after the proxy was returned, i.e. the bean or
 * a bean it depends on has changed. Otherwise it keeps its bean and its handler. Until a refreshed one is called, its
 * invocation handler is not the one of its library.
 * <p>
 * A proxy of a bean of a scope (e.g. of a request or a session) is not refreshed: a reload does not remove the beans
 * from their scopes, so the proxy is still the bean of its scope.
 * <p>
 * The new invocation handler may change the result of hashCode() and equals() of the proxy, e.g. the one of Spring
 * AOP takes it from the target. A collection that hashed the proxy before the reload does not find it then.
 */
public class JdkProxyRefresher {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(JdkProxyRefresher.class);

    // tracked proxies by their identity hash code
    private static final Map<Integer, List<TrackedProxy>> proxies = new HashMap<>();
    // proxies collected by the garbage collector, their entries are removed right away
    private static final ReferenceQueue<Object> collectedProxies = new ReferenceQueue<>();
    // Counts the tracked proxies and the destroyed beans. A proxy returned after its bean was destroyed already
    // calls the new bean.
    private static long sequence;
    // the beans destroyed since the last refresh of their bean factory: bean name -> sequence
    private static final Map<Object, Map<String, Long>> destroyedBeans = new WeakHashMap<>();

    // guards the invocation handler of the proxies, which is set by a reload and by the threads calling the proxies
    private static final Object HANDLER_LOCK = new Object();

    private static Field handlerField;
    private static boolean handlerFieldResolved;

    private static final class ProxyReference extends WeakReference<Object> {
        final int identityHashCode;

        ProxyReference(Object proxy) {
            super(proxy, collectedProxies);
            this.identityHashCode = System.identityHashCode(proxy);
        }
    }

    private static final class TrackedProxy {
        final ProxyReference proxy;
        final WeakReference<Object> beanFactory;
        final Class<?>[] paramClasses;
        final Arguments arguments;
        final long sequence;

        TrackedProxy(Object proxy, Object beanFactory, Class<?>[] paramClasses, Object[] paramValues, long sequence) {
            this.proxy = new ProxyReference(proxy);
            this.beanFactory = new WeakReference<>(beanFactory);
            this.paramClasses = paramClasses;
            this.arguments = new Arguments(paramValues);
            this.sequence = sequence;
        }

        boolean isAlive() {
            return proxy.get() != null && beanFactory.get() != null;
        }
    }

    /**
     * The arguments of the getBean method that returned a proxy. A tracked proxy is kept until its proxy is collected,
     * so it must not keep the proxy alive: an argument the bean is created with may reference the proxy, e.g. the
     * owner of the bean. Such arguments are held weakly, the bean usually keeps them too. Names, classes and other
     * values of the JDK cannot reference the proxy.
     */
    private static final class Arguments {
        private final Class<?> componentType;
        private final Object[] held;

        Arguments(Object[] values) {
            componentType = values.getClass().getComponentType();
            held = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Object[]) {
                    // the arguments to create the bean with
                    held[i] = new Arguments((Object[]) value);
                } else {
                    held[i] = isValue(value) ? value : new WeakArgument(value);
                }
            }
        }

        /**
         * Values of the JDK, which cannot reference the proxy. They are often created for the call only.
         */
        private static boolean isValue(Object value) {
            if (value == null || value instanceof Class || value instanceof Enum) {
                return true;
            }
            Class<?> type = value.getClass();
            if (type.isArray()) {
                return type.getComponentType().isPrimitive();
            }
            if (type.getClassLoader() != null) {
                // not a class of the JDK, e.g. a Number of the application
                return false;
            }
            return value instanceof String || value instanceof Boolean || value instanceof Character
                    || value instanceof Number || value instanceof Date || value instanceof UUID
                    || value instanceof Locale || value instanceof Currency || value instanceof URI
                    || value instanceof URL || value instanceof File || type.getName().startsWith("java.time.");
        }

        /**
         * @return the arguments, or null if one of them was collected
         */
        Object[] get() {
            Object[] values = (Object[]) Array.newInstance(componentType, held.length);
            for (int i = 0; i < held.length; i++) {
                Object value = held[i];
                if (value instanceof Arguments) {
                    value = ((Arguments) value).get();
                } else if (value instanceof WeakArgument) {
                    value = ((WeakArgument) value).get();
                } else {
                    values[i] = value;
                    continue;
                }
                if (value == null) {
                    return null;
                }
                values[i] = value;
            }
            return values;
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder("[");
            for (int i = 0; i < held.length; i++) {
                text.append(i == 0 ? "" : ", ").append(held[i] instanceof WeakArgument ? "?" : held[i]);
            }
            return text.append(']').toString();
        }
    }

    private static final class WeakArgument extends WeakReference<Object> {
        WeakArgument(Object value) {
            super(value);
        }
    }

    /**
     * Remembers a JDK proxy returned by the getBean method of a bean factory.
     *
     * @param beanFactory  the bean factory
     * @param proxy        the JDK proxy
     * @param paramClasses parameter classes of the getBean method
     * @param paramValues  parameter values of the getBean method
     */
    public static void track(Object beanFactory, Object proxy, Class<?>[] paramClasses, Object[] paramValues) {
        synchronized (proxies) {
            removeCollected();
            List<TrackedProxy> list = proxies.computeIfAbsent(System.identityHashCode(proxy), k -> new ArrayList<>(1));
            for (Iterator<TrackedProxy> it = list.iterator(); it.hasNext(); ) {
                TrackedProxy trackedProxy = it.next();
                if (trackedProxy.proxy.get() == proxy) {
                    return;
                } else if (!trackedProxy.isAlive()) {
                    it.remove();
                }
            }
            list.add(new TrackedProxy(proxy, beanFactory, paramClasses, paramValues, ++sequence));
        }
    }

    /**
     * Remembers that a bean was destroyed. A reload destroys the changed beans, and Spring the beans that depend on
     * them, also prototypes and beans of a scope, of which the bean factory keeps no instance. Called for each
     * destroyed bean of a bean factory.
     */
    public static void beanDestroyed(Object beanFactory, String beanName) {
        synchronized (proxies) {
            // only a proxy tracked before can call the destroyed bean
            if (!proxies.isEmpty()) {
                destroyedBeans.computeIfAbsent(beanFactory, k -> new HashMap<>()).put(beanName, ++sequence);
            }
        }
    }

    /**
     * Forgets the proxies of a bean factory that is destroyed.
     */
    public static void removeBeanFactory(Object beanFactory) {
        removeIf(trackedProxy -> !trackedProxy.isAlive() || trackedProxy.beanFactory.get() == beanFactory);
        synchronized (proxies) {
            destroyedBeans.remove(beanFactory);
        }
    }

    /**
     * Replaces the invocation handler of the tracked JDK proxies of the bean factory, so that they call the new beans.
     * Called at the end of a reload, also of a failed one, which may have destroyed beans.
     * <p>
     * A bean of the bean factory may be tracked for a child bean factory: Spring gets the bean of a parent without
     * the getBean method of the parent, so the proxy is known only to the child that returned it.
     *
     * @param beanFactory the reloaded bean factory
     */
    public static void refresh(ConfigurableListableBeanFactory beanFactory) {
        removeIf(trackedProxy -> !trackedProxy.isAlive());
        List<TrackedProxy> snapshot = new ArrayList<>();
        Map<String, Long> destroyed;
        synchronized (proxies) {
            // They are forgotten, also after a failed reload: the next reload must not take them as destroyed again.
            destroyed = destroyedBeans.remove(beanFactory);
            for (List<TrackedProxy> list : proxies.values()) {
                for (TrackedProxy trackedProxy : list) {
                    if (isSameOrChild(trackedProxy.beanFactory.get(), beanFactory)) {
                        snapshot.add(trackedProxy);
                    }
                }
            }
        }
        if (destroyed == null) {
            destroyed = Collections.emptyMap();
        }
        for (TrackedProxy trackedProxy : snapshot) {
            Object proxy = trackedProxy.proxy.get();
            Object trackedBeanFactory = trackedProxy.beanFactory.get();
            if (proxy != null && trackedBeanFactory instanceof ConfigurableListableBeanFactory) {
                try {
                    refresh((ConfigurableListableBeanFactory) trackedBeanFactory, beanFactory, destroyed, proxy,
                            trackedProxy);
                } catch (RuntimeException | LinkageError e) {
                    // also a class that cannot be loaded after a change, the other proxies are still refreshed
                    LOGGER.warning("Unable to refresh the proxy of the bean {}, it calls the old bean: {}",
                            trackedProxy.arguments, e.toString());
                }
            }
        }
    }

    private static boolean isSameOrChild(Object beanFactory, Object parent) {
        Object factory = beanFactory;
        while (factory != null && factory != parent) {
            factory = factory instanceof HierarchicalBeanFactory
                    ? ((HierarchicalBeanFactory) factory).getParentBeanFactory() : null;
        }
        return factory != null;
    }

    /**
     * @param beanFactory the bean factory that returned the proxy
     * @param reloaded    the reloaded bean factory, the same one or one of its parents
     * @param destroyed   the beans of the reloaded bean factory destroyed since its last refresh
     */
    private static void refresh(ConfigurableListableBeanFactory beanFactory, ConfigurableListableBeanFactory reloaded,
                                Map<String, Long> destroyed, Object proxy, TrackedProxy trackedProxy) {
        Object[] paramValues = trackedProxy.arguments.get();
        if (paramValues == null) {
            LOGGER.info("An argument of the bean {} is not available anymore, its proxy still calls the old bean",
                    trackedProxy.arguments);
            return;
        }
        String beanName = getBeanName(beanFactory, paramValues);
        if (beanName != null) {
            // containsBean() includes the beans of parent bean factories
            if (!beanFactory.containsBean(beanName)) {
                // it stays tracked, the bean may be defined again by a later reload
                LOGGER.debug("The bean {} was removed, its proxy is not refreshed", beanName);
                return;
            }
            if (getOwner(beanFactory, beanName) != reloaded) {
                // a bean of a child or of a parent of the reloaded bean factory, it is not reloaded
                return;
            }
            // the alias may be known to the reloaded bean factory only
            beanName = canonicalName(reloaded, beanName);
        }
        boolean singletonExists = beanName != null && reloaded.containsSingleton(beanName);
        if (!singletonExists || !reloaded.isSingleton(beanName)) {
            // The scope by the bean definition: isSingleton() of the bean factory would create a factory bean to ask
            // it. An existing singleton that is not one is a factory bean of prototypes.
            BeanDefinition definition = beanName == null ? null : getBeanDefinition(reloaded, beanName);
            boolean singleton = definition == null || definition.isSingleton();
            if (!singleton && !definition.isPrototype()) {
                // The bean of a scope, e.g. of a session. The reload does not remove the beans from their scopes, so
                // the proxy is still the bean of its scope. Another caller would get the bean of another scope.
                return;
            }
            if (beanName != null && (singletonExists || !singleton)
                    && !isDestroyed(reloaded, destroyed, beanName, trackedProxy)) {
                // A prototype or a bean of a scope that was not destroyed since its proxy was returned keeps its bean
                // and its state, and its proxy the handler of its library (#327).
                return;
            }
            // get the bean when it is used, e.g. a new prototype
            setHandler(proxy, new RefreshingInvocationHandler(beanFactory, trackedProxy.paramClasses, paramValues,
                    RefreshingInvocationHandler.getPrevious(proxy), null));
            return;
        }
        Object bean = getBean(beanFactory, trackedProxy.paramClasses, paramValues);
        if (bean == proxy) {
            return;
        }
        if (bean != null && bean.getClass() == proxy.getClass()) {
            setHandler(proxy, RefreshingInvocationHandler.getHandler(bean));
        } else if (!RefreshingInvocationHandler.isCalling(proxy, bean)) {
            // e.g. the bean implements other interfaces now
            LOGGER.info("The bean {} is not a proxy of the class {} anymore, the old proxy calls it by reflection",
                    beanName, proxy.getClass().getName());
            setHandler(proxy, new RefreshingInvocationHandler(beanFactory, trackedProxy.paramClasses, paramValues,
                    RefreshingInvocationHandler.getPrevious(proxy), bean));
        }
    }

    /**
     * The bean factory that defines the bean: the one that returned the proxy or one of its parents.
     */
    private static BeanFactory getOwner(ConfigurableListableBeanFactory beanFactory, String beanName) {
        BeanFactory factory = beanFactory;
        while (factory instanceof HierarchicalBeanFactory) {
            HierarchicalBeanFactory current = (HierarchicalBeanFactory) factory;
            if (current.containsLocalBean(beanName)) {
                return current;
            }
            factory = current.getParentBeanFactory();
        }
        return beanFactory;
    }

    private static String canonicalName(Object beanFactory, String beanName) {
        return beanFactory instanceof SimpleAliasRegistry
                ? ((SimpleAliasRegistry) beanFactory).canonicalName(beanName) : beanName;
    }

    private static BeanDefinition getBeanDefinition(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getMergedBeanDefinition(beanName);
        } catch (NoSuchBeanDefinitionException e) {
            // e.g. a singleton registered without a definition
            return null;
        }
    }

    /**
     * Checks if the bean was destroyed after its proxy was returned. A proxy returned later, e.g. to a bean created
     * by the reload, already calls the new bean. Without the hook of the plugin in the bean factory it is not known,
     * the bean is taken as destroyed then.
     */
    private static boolean isDestroyed(ConfigurableListableBeanFactory reloaded, Map<String, Long> destroyed,
                                       String beanName, TrackedProxy trackedProxy) {
        if (!(reloaded instanceof BeanFactoryLifecycle)) {
            return true;
        }
        Long destroyedAt = destroyed.get(beanName);
        return destroyedAt != null && trackedProxy.sequence < destroyedAt;
    }

    private static String getBeanName(ConfigurableListableBeanFactory beanFactory, Object[] paramValues) {
        Object first = paramValues.length > 0 ? paramValues[0] : null;
        if (first instanceof String) {
            // an alias of the bean
            return canonicalName(beanFactory, BeanFactoryUtils.transformedBeanName((String) first));
        }
        if (first instanceof Class) {
            // do not create beans to find their type
            String[] names = beanFactory.getBeanNamesForType((Class<?>) first, true, false);
            return names.length == 1 ? names[0] : null;
        }
        return null;
    }

    /**
     * For tests: checks if the proxy is tracked.
     */
    static boolean isTracked(Object proxy) {
        synchronized (proxies) {
            List<TrackedProxy> list = proxies.get(System.identityHashCode(proxy));
            return list != null && list.stream().anyMatch(trackedProxy -> trackedProxy.proxy.get() == proxy);
        }
    }

    private static void removeCollected() {
        Reference<?> reference;
        while ((reference = collectedProxies.poll()) != null) {
            int key = ((ProxyReference) reference).identityHashCode;
            List<TrackedProxy> list = proxies.get(key);
            if (list != null) {
                Reference<?> collected = reference;
                list.removeIf(trackedProxy -> trackedProxy.proxy == collected);
                if (list.isEmpty()) {
                    proxies.remove(key);
                }
            }
        }
    }

    private static void removeIf(java.util.function.Predicate<TrackedProxy> predicate) {
        synchronized (proxies) {
            for (Iterator<List<TrackedProxy>> it = proxies.values().iterator(); it.hasNext(); ) {
                List<TrackedProxy> list = it.next();
                list.removeIf(predicate);
                if (list.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    static Object getBean(Object beanFactory, Class<?>[] paramClasses, Object[] paramValues) {
        try {
            Method method = beanFactory.getClass().getMethod(ProxyReplacer.FACTORY_METHOD_NAME, paramClasses);
            return method.invoke(beanFactory, paramValues);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets the invocation handler of a proxy.
     *
     * @return false if the handler of proxies is not accessible
     */
    static boolean setHandler(Object proxy, InvocationHandler handler) {
        Field field = getHandlerField();
        if (field == null) {
            return false;
        }
        try {
            synchronized (HANDLER_LOCK) {
                field.set(proxy, handler);
            }
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.warning("Unable to set the invocation handler of the proxy {}", e, proxy.getClass().getName());
            return false;
        }
    }

    /**
     * Sets the invocation handler of a proxy, unless another thread or a later reload has replaced the expected
     * one meanwhile.
     *
     * @return the handler of the proxy after the call
     */
    static InvocationHandler replaceHandler(Object proxy, InvocationHandler expected, InvocationHandler handler) {
        synchronized (HANDLER_LOCK) {
            InvocationHandler current = Proxy.getInvocationHandler(proxy);
            return current == expected && setHandler(proxy, handler) ? handler : current;
        }
    }

    /**
     * The field java.lang.reflect.Proxy.h with the invocation handler of a proxy, or null if it is not accessible.
     */
    private static synchronized Field getHandlerField() {
        if (!handlerFieldResolved) {
            handlerFieldResolved = true;
            try {
                Field field = Proxy.class.getDeclaredField("h");
                openJavaLangReflect();
                field.setAccessible(true);
                handlerField = field;
            } catch (Exception e) {
                LOGGER.warning("Unable to access the invocation handler of JDK proxies, JDK proxy beans are not "
                        + "refreshed after a reload: {}", e.toString());
            }
        }
        return handlerField;
    }

    /**
     * Since Java 9 the package java.lang.reflect of the module java.base is not open. The agent opens it to the module
     * of this class with Instrumentation.redefineModule(), called by reflection, as the agent is built for Java 8.
     */
    private static void openJavaLangReflect() throws Exception {
        Method getModule;
        try {
            getModule = Class.class.getMethod("getModule");
        } catch (NoSuchMethodException e) {
            // Java 8, no modules
            return;
        }
        Class<?> moduleClass = Class.forName("java.lang.Module");
        Object javaBase = getModule.invoke(Proxy.class);
        Object module = getModule.invoke(JdkProxyRefresher.class);
        Instrumentation instrumentation = PluginManager.getInstance().getInstrumentation();
        Method redefineModule = Instrumentation.class.getMethod("redefineModule", moduleClass,
                Set.class, Map.class, Map.class, Set.class, Map.class);
        redefineModule.invoke(instrumentation, javaBase, Collections.emptySet(), Collections.emptyMap(),
                Collections.singletonMap("java.lang.reflect", Collections.singleton(module)),
                Collections.emptySet(), Collections.emptyMap());
    }
}
