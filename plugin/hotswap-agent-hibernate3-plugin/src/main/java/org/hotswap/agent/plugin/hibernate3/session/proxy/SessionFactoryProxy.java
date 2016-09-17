/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private static Map<Configuration, SessionFactoryProxy> proxiedFactories = new HashMap<Configuration, SessionFactoryProxy>();

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
