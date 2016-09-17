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
package org.hotswap.agent.plugin.hibernate3.jpa.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.spi.PersistenceUnitInfo;

import org.hibernate.Version;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Create a proxy for EntityManagerFactory and register all created proxies.
 * Provide static method to reload a proxied factory.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class EntityManagerFactoryProxy {
    
    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(EntityManagerFactoryProxy.class);
    
    /** The proxied factories. */
    // Map persistenceUnitName -> Wrapper instance
    private static Map<String, EntityManagerFactoryProxy> proxiedFactories = new HashMap<String, EntityManagerFactoryProxy>();
    
    /** The reload lock. */
    // hold lock during refresh. The lock is checked on each factory method call.
    final Object reloadLock = new Object();
    
    /** The current instance. */
    // current entity manager factory instance - this is the target this proxy delegates to
    EntityManagerFactory currentInstance;
    
    /** The persistence unit name. */
    // info and properties to use to build fresh instance of factory
    String persistenceUnitName;
    
    /** The info. */
    PersistenceUnitInfo info;
    
    /** The properties. */
    Map<?,?> properties;

    /**
     * Create new wrapper for persistenceUnitName and hold it's instance for future use.
     *
     * @param persistenceUnitName key to the wrapper
     * @return existing wrapper or new instance (never null)
     */
    public static EntityManagerFactoryProxy getWrapper(String persistenceUnitName) {
        if (!proxiedFactories.containsKey(persistenceUnitName)) {
            proxiedFactories.put(persistenceUnitName, new EntityManagerFactoryProxy());
        }
        return proxiedFactories.get(persistenceUnitName);
    }

    /**
     * Refresh all known wrapped factories.
     */
    public static void refreshProxiedFactories() {
        String[] version = Version.getVersionString().split("\\.");
        boolean version43OrGreater = false;
        try {
            version43OrGreater = Integer.valueOf(version[0]) >= 4 && Integer.valueOf(version[1]) >= 3;
        } catch (Exception e) {
            LOGGER.warning("Unable to resolve hibernate version '{}'", Arrays.toString(version));
        }

        for (EntityManagerFactoryProxy wrapper : proxiedFactories.values())
            try {
                // lock proxy execution during reload
                synchronized (wrapper.reloadLock) {
                    if (version43OrGreater) {
                        wrapper.refreshProxiedFactoryVersion43OrGreater();
                    } else {
                        wrapper.refreshProxiedFactory();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    /**
     * Refresh proxied factory version43 or greater.
     */
    public void refreshProxiedFactoryVersion43OrGreater() {
        if (info == null) {
            currentInstance = Persistence.createEntityManagerFactory(persistenceUnitName, properties);
        } else {
            try {
                Class<?> bootstrapClazz = loadClass("org.hibernate.jpa.boot.spi.Bootstrap");
                Class<?> builderClazz = loadClass("org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder");

                Object builder = ReflectionHelper.invoke(null, bootstrapClazz, "getEntityManagerFactoryBuilder",
                        new Class[]{PersistenceUnitInfo.class, Map.class}, info, properties);

                currentInstance = (EntityManagerFactory) ReflectionHelper.invoke(builder, builderClazz, "build",
                        new Class[]{});
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("Unable to reload persistence unit {}", info, e);
            }
        }
    }

    /**
     * Refresh a single persistence unit - replace the wrapped EntityManagerFactory with fresh instance.
     *
     * @throws NoSuchMethodException the no such method exception
     * @throws InvocationTargetException the invocation target exception
     * @throws IllegalAccessException the illegal access exception
     * @throws NoSuchFieldException the no such field exception
     */
    public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        // refresh registry
        try {
            Class<?> entityManagerFactoryRegistryClazz = loadClass("org.hibernate.ejb.internal.EntityManagerFactoryRegistry");
            Object instance = ReflectionHelper.get(null, entityManagerFactoryRegistryClazz, "INSTANCE");
            ReflectionHelper.invoke(instance, entityManagerFactoryRegistryClazz, "removeEntityManagerFactory",
                    new Class[] {String.class, EntityManagerFactory.class}, persistenceUnitName, currentInstance);
        } catch (Exception e) {
            LOGGER.error("Unable to clear previous instance of entity manager factory");
        }


        buildFreshEntityManagerFactory();
    }

    // create factory from cached configuration
    /**
     * Builds the fresh entity manager factory.
     */
    // from HibernatePersistence.createContainerEntityManagerFactory()
    private void buildFreshEntityManagerFactory() {
        try {
            Class<?> ejb3ConfigurationClazz = loadClass("org.hibernate.ejb.Ejb3Configuration");
            LOGGER.trace("new Ejb3Configuration()");
            Object cfg = ejb3ConfigurationClazz.newInstance();

            LOGGER.trace("cfg.configure( info, properties );");

            if (info != null) {
                ReflectionHelper.invoke(cfg, ejb3ConfigurationClazz, "configure",
                        new Class[]{PersistenceUnitInfo.class, Map.class}, info, properties);
            }
            else {
                ReflectionHelper.invoke(cfg, ejb3ConfigurationClazz, "configure",
                        new Class[]{String.class, Map.class}, persistenceUnitName, properties);
            }

            LOGGER.trace("configured.buildEntityManagerFactory()");
            currentInstance = (EntityManagerFactory) ReflectionHelper.invoke(cfg, ejb3ConfigurationClazz, "buildEntityManagerFactory",
                    new Class[]{});


        } catch (Exception e) {
            LOGGER.error("Unable to build fresh entity manager factory for persistence unit {}", persistenceUnitName);
        }
    }

    /**
     * Create a proxy for EntityManagerFactory.
     *
     * @param factory    initial factory to delegate method calls to.
     * @param persistenceUnitName the persistence unit name
     * @param info       definition to cache for factory reload
     * @param properties properties to cache for factory reload
     * @return the proxy
     */
    public EntityManagerFactory proxy(EntityManagerFactory factory, String persistenceUnitName,
                                      PersistenceUnitInfo info, Map<?,?> properties) {
        this.currentInstance = factory;
        this.persistenceUnitName = persistenceUnitName;
        this.info = info;
        this.properties = properties;

        return (EntityManagerFactory) Proxy.newProxyInstance(
                currentInstance.getClass().getClassLoader(), currentInstance.getClass().getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // if reload in progress, wait for it
                        synchronized (reloadLock) {}

                        return method.invoke(currentInstance, args);
                    }
                });
    }

    /**
     * Load class.
     *
     * @param name the name
     * @return the class
     * @throws ClassNotFoundException the class not found exception
     */
    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return getClass().getClassLoader().loadClass(name);
    }
}
