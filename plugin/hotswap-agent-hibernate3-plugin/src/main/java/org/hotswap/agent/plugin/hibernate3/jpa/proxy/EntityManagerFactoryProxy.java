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
    private static AgentLogger LOGGER = AgentLogger.getLogger(EntityManagerFactoryProxy.class);
    // Map persistenceUnitName -> Wrapper instance
    private static Map<String, EntityManagerFactoryProxy> proxiedFactories = new HashMap<String, EntityManagerFactoryProxy>();
    // hold lock during refresh. The lock is checked on each factory method call.
    final Object reloadLock = new Object();
    // current entity manager factory instance - this is the target this proxy delegates to
    EntityManagerFactory currentInstance;
    // info and properties to use to build fresh instance of factory
    String persistenceUnitName;
    PersistenceUnitInfo info;
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

    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return getClass().getClassLoader().loadClass(name);
    }
}
