package org.hotswap.agent.plugin.hibernate;

import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.ejb.internal.EntityManagerFactoryRegistry;
import org.hotswap.agent.logging.AgentLogger;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class EntityManagerFactoryWrapper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(EntityManagerFactoryWrapper.class);

    // Map persistenceUnitName -> Wrapper instance
    private static Map<String, EntityManagerFactoryWrapper> proxiedFactories = new HashMap<String, EntityManagerFactoryWrapper>();

    /**
     * Create new wrapper for persistenceUnitName and hold it's instance for future use.
     *
     * @param persistenceUnitName key to the wrapper
     * @return existing wrapper or new instance (never null)
     */
    public static EntityManagerFactoryWrapper getWrapper(String persistenceUnitName) {
        if (!proxiedFactories.containsKey(persistenceUnitName)) {
            proxiedFactories.put(persistenceUnitName, new EntityManagerFactoryWrapper());
        }
        return proxiedFactories.get(persistenceUnitName);
    }

    /**
     * Refresh all known wrapped factories.
     */
    public static void refreshProxiedFactories() {
        for (EntityManagerFactoryWrapper wrapper : proxiedFactories.values())
            try {
                // lock proxy execution during reload
                synchronized (wrapper.reloadLock) {
                    wrapper.refreshProxiedFactory();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    /**
     * Refresh a single persistence unit - replace the wrapped EntityManagerFactory with fresh instance.
     */
    public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        // refresh registry
        EntityManagerFactoryRegistry.INSTANCE.removeEntityManagerFactory(info.getPersistenceUnitName(), currentInstance);

        // from HibernatePersistence.createContainerEntityManagerFactory()
        buildFreshEntityManagerFactory();
    }


    // create factory from cached configuration
    private void buildFreshEntityManagerFactory() {
        LOGGER.trace("new Ejb3Configuration()");
        Ejb3Configuration cfg = new Ejb3Configuration();
        LOGGER.trace("cfg.configure( info, properties );");
        Ejb3Configuration configured = cfg.configure(info, properties);
        LOGGER.trace("configured.buildEntityManagerFactory()");
        currentInstance = configured != null ? configured.buildEntityManagerFactory() : null;
    }

    // hold lock during refresh. The lock is checked on each factory method call.
    Object reloadLock = new Object();

    // current entity manager factory instance - this is the target this proxy delegates to
    EntityManagerFactory currentInstance;

    // info and properties to use to build fresh instance of factory
    PersistenceUnitInfo info;
    Map properties;

    /**
     * Create a proxy for EntityManagerFactory.
     *
     * @param factory    initial factory to delegate method calls to.
     * @param info       definition to cache for factory reload
     * @param properties properties to cache for factory reload
     * @return the proxy
     */
    public EntityManagerFactory proxy(EntityManagerFactory factory, PersistenceUnitInfo info, Map properties) {
        this.currentInstance = factory;
        this.info = info;
        this.properties = properties;

        return (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class[]{EntityManagerFactory.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // if reload in progress, wait for it
                        synchronized (reloadLock) {
                        }
                        ;

                        return method.invoke(currentInstance, args);
                    }
                });
    }
}
