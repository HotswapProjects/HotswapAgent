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

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Invocation handler set to a JDK proxy bean by a reload, which gets the bean from the bean factory when the proxy
 * is called (in the thread of the caller, e.g. in the scope of a request). If the new bean is a proxy of the same
 * class, its invocation handler replaces this one. If the proxy itself is still the bean, its previous handler is set
 * back. Otherwise the calls are passed to the new bean by reflection.
 * <p>
 * Until the proxy is called, code that casts its invocation handler does not find the handler it expects. So this
 * handler is set only to proxies whose bean was destroyed.
 */
public class RefreshingInvocationHandler implements InvocationHandler, Serializable {
    private static final long serialVersionUID = 1L;

    private final transient Object beanFactory;
    private final transient Class<?>[] paramClasses;
    private final transient Object[] paramValues;
    private final transient InvocationHandler previous;
    private transient volatile Object bean;

    /**
     * What a call of the proxy is passed to: the handler of the proxy, or the bean, which is called by reflection.
     */
    private static final class Target {
        final InvocationHandler handler;
        final Object bean;

        Target(InvocationHandler handler, Object bean) {
            this.handler = handler;
            this.bean = bean;
        }
    }

    /**
     * @param previous the handler of the proxy before the reload
     * @param bean     the new bean if it is known, it is called by reflection
     */
    RefreshingInvocationHandler(Object beanFactory, Class<?>[] paramClasses, Object[] paramValues,
                                InvocationHandler previous, Object bean) {
        this.beanFactory = beanFactory;
        this.paramClasses = paramClasses;
        this.paramValues = paramValues;
        this.previous = previous;
        this.bean = bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean objectMethod = method.getDeclaringClass() == Object.class;
        Object current = bean;
        if (current == null) {
            Target target;
            try {
                target = resolve(proxy);
            } catch (RuntimeException e) {
                if (objectMethod) {
                    // e.g. hashCode() or toString() of a bean of a request, called outside of a request
                    return invokeObjectMethod(proxy, method, args);
                }
                throw e;
            }
            if (target.handler != null) {
                return target.handler.invoke(proxy, method, args);
            }
            current = target.bean;
        }
        if (objectMethod && (current == null || !method.getName().equals("toString"))) {
            // equals and hashCode of the proxy itself, it is not the new bean
            return invokeObjectMethod(proxy, method, args);
        }
        if (current == null) {
            throw new IllegalStateException("The bean " + Arrays.toString(paramValues) + " is null");
        }
        if (!method.getDeclaringClass().isInstance(current)) {
            throw new IllegalStateException("The bean " + current.getClass().getName() + " does not implement "
                    + method.getDeclaringClass().getName() + " anymore");
        }
        makeAccessible(method);
        try {
            return method.invoke(current, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            default:
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
        }
    }

    /**
     * A method of an interface that is not public cannot be called from this package otherwise.
     */
    private static void makeAccessible(Method method) {
        if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            try {
                method.setAccessible(true);
            } catch (RuntimeException e) {
                // not allowed, e.g. by the module of the interface: the call fails with IllegalAccessException
            }
        }
    }

    /**
     * Gets the bean and sets the invocation handler of the proxy.
     *
     * @return the handler of the proxy or the bean to call
     */
    private Target resolve(Object proxy) {
        InvocationHandler installed = Proxy.getInvocationHandler(proxy);
        if (installed != this) {
            // resolved by another thread, or replaced by a later reload
            return new Target(installed, null);
        }
        // Not under a lock, the bean factory takes its own locks to create the bean.
        Object current = bean != null ? bean : JdkProxyRefresher.getBean(beanFactory, paramClasses, paramValues);
        InvocationHandler handler = null;
        if (current == proxy) {
            // the proxy is still the bean
            handler = previous;
        } else if (current != null && current.getClass() == proxy.getClass()) {
            handler = getHandler(current);
        }
        if (handler != null) {
            // Another thread may have got another bean (e.g. a prototype) meanwhile, all callers use the same one.
            installed = JdkProxyRefresher.replaceHandler(proxy, this, handler);
            if (installed != this) {
                return new Target(installed, null);
            }
        }
        synchronized (this) {
            if (bean == null) {
                bean = current;
            }
            return new Target(null, bean);
        }
    }

    /**
     * The handler of a proxy before it was refreshed by a handler of this class, if it was.
     */
    static InvocationHandler getPrevious(Object proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        return handler instanceof RefreshingInvocationHandler ? ((RefreshingInvocationHandler) handler).previous : handler;
    }

    /**
     * Checks if the proxy already calls the bean by reflection.
     */
    static boolean isCalling(Object proxy, Object bean) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        return bean != null && handler instanceof RefreshingInvocationHandler
                && ((RefreshingInvocationHandler) handler).bean == bean;
    }

    /**
     * The handler of a proxy, resolved if the proxy is refreshed by a handler of this class too.
     */
    static InvocationHandler getHandler(Object proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof RefreshingInvocationHandler) {
            InvocationHandler resolved = ((RefreshingInvocationHandler) handler).resolve(proxy).handler;
            return resolved != null ? resolved : handler;
        }
        return handler;
    }

    /**
     * A proxy that is serialized before it is called (e.g. a bean in a persisted HTTP session) is written with the
     * handler it had before the reload, as this one cannot be read back without the bean factory.
     */
    private Object writeReplace() {
        return previous;
    }
}
