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
package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.impl.SessionFactoryImpl;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;

/**
 * For Hibernate without EJB (EntityManager).
 * <p/>
 * TODO - Not tested, some additional Configuration cleanup may be necessary
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */
@SuppressWarnings("restriction")
public class SessionFactoryProxy {

    /** The proxied factories. */
    private static Map<Configuration, SessionFactoryProxy> proxiedFactories = new HashMap<>();

    /**
     * Gets the wrapper.
     *
     * @param configuration
     *            the configuration
     * @return the wrapper
     */
    public static SessionFactoryProxy getWrapper(Configuration configuration) {
        synchronized (proxiedFactories) {
            if (!proxiedFactories.containsKey(configuration)) {
                proxiedFactories.put(configuration, new SessionFactoryProxy(configuration));
            }
            return proxiedFactories.get(configuration);
        }
    }

    /**
     * Refresh proxied factories.
     */
    public static void refreshProxiedFactories() {
        synchronized (proxiedFactories) {
            for (SessionFactoryProxy wrapper : proxiedFactories.values()) {
                try {
                    wrapper.refreshProxiedFactory();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** The configuration. */
    private Configuration configuration;

    /** The current instance. */
    private volatile SessionFactory currentInstance;

    /**
     * Instantiates a new session factory proxy.
     *
     * @param configuration
     *            the configuration
     */
    private SessionFactoryProxy(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Refresh proxied factory.
     *
     * @throws NoSuchMethodException
     *             the no such method exception
     * @throws InvocationTargetException
     *             the invocation target exception
     * @throws IllegalAccessException
     *             the illegal access exception
     */
    public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ReInitializable r = ReInitializable.class.cast(configuration);
        r.hotSwap();
        currentInstance = r._buildSessionFactory();
    }

    /**
     * Proxy.
     *
     * @param sessionFactory
     *            the session factory
     * @return the session factory
     */
    public SessionFactory proxy(SessionFactory sessionFactory) {
        try {
            this.currentInstance = sessionFactory;

            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(SessionFactoryImpl.class);
            factory.setInterfaces(new Class[] { SessionFactory.class, SessionFactoryImplementor.class });

            MethodHandler handler = new MethodHandler() {
                @Override
                public Object invoke(Object self, Method overridden, Method forwarder, Object[] args) throws Throwable {
                    return overridden.invoke(currentInstance, args);
                }
            };

            Object instance;
            try {
                Constructor<?> constructor = sun.reflect.ReflectionFactory.getReflectionFactory()//
                        .newConstructorForSerialization(factory.createClass(), Object.class.getDeclaredConstructor(new Class[0]));
                instance = constructor.newInstance();
                ((Proxy) instance).setHandler(handler);
            } catch (Exception e) {
                e.printStackTrace();
                throw new Error("Unable instantiate SessionFactory proxy", e);
            }

            return (SessionFactory) instance;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("Unable instantiate SessionFactory proxy", e);
        }
    }
}
