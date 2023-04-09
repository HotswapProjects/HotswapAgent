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
package org.hotswap.agent.plugin.hibernate.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.service.ServiceRegistry;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;

import sun.reflect.ReflectionFactory;

/**
 * For Hibernate without EJB (EntityManager).
 * <p/>
 * TODO - Not tested, some additional Configuration cleanup may be necessary
 *
 * @author Jiri Bubnik
 */
public class SessionFactoryProxy {
    private static Map<Configuration, SessionFactoryProxy> proxiedFactories = new HashMap<>();

    private SessionFactoryProxy(Configuration configuration) {
        this.configuration = configuration;
    }

    public static SessionFactoryProxy getWrapper(Configuration configuration) {
        if (!proxiedFactories.containsKey(configuration)) {
            proxiedFactories.put(configuration, new SessionFactoryProxy(configuration));
        }

        return proxiedFactories.get(configuration);
    }

    public static void refreshProxiedFactories() {
        for (SessionFactoryProxy wrapper : proxiedFactories.values())
            try {
                wrapper.refreshProxiedFactory();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = Configuration.class.getDeclaredMethod("_buildSessionFactory", ServiceRegistry.class);
        currentInstance = (SessionFactory) m.invoke(configuration, serviceRegistry);
    }

    private Configuration configuration;
    private SessionFactory currentInstance;
    private ServiceRegistry serviceRegistry;

    public SessionFactory proxy(SessionFactory sessionFactory, ServiceRegistry serviceRegistry) {
        this.currentInstance = sessionFactory;
        this.serviceRegistry = serviceRegistry;

        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(SessionFactoryImpl.class);
        factory.setInterfaces(new Class[]{SessionFactory.class});

        MethodHandler handler = new MethodHandler() {
            @Override
            public Object invoke(Object self, Method overridden, Method forwarder,
                                 Object[] args) throws Throwable {
                try {
                    return overridden.invoke(currentInstance, args);
                } catch(InvocationTargetException e) {
                    //rethrow original exception to prevent proxying from changing external behaviour of SessionFactory
                    throw e.getTargetException();
                }
            }
        };


        Object instance;
        try {
            Constructor constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(factory.createClass(), Object.class.getDeclaredConstructor(new Class[0]));
            instance = constructor.newInstance();
            ((Proxy) instance).setHandler(handler);
        } catch (Exception e) {
            throw new Error("Unable instantiate SessionFactory proxy", e);
        }

        return (SessionFactory) instance;
    }
}
