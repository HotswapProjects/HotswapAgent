package org.hotswap.agent.plugin.hibernate;

import org.hibernate.Version;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.Map;

/**
 * Helper to create a proxy for entity manager factory.
 *
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class HibernatePersistenceHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernatePersistenceHelper.class);

    /**
     * @param info       persistent unit definition
     * @param properties properties to create entity manager factory
     * @param original   entity manager factory
     * @return
     */
    public static EntityManagerFactory createContainerEntityManagerFactoryProxy(PersistenceUnitInfo info, Map properties,
                                                                                EntityManagerFactory original) {
        EntityManagerFactoryWrapper wrapper = EntityManagerFactoryWrapper.getWrapper(info.getPersistenceUnitName());
        EntityManagerFactory proxy = wrapper.proxy(original, info.getPersistenceUnitName(), info, properties);

        initPlugin(original);

        LOGGER.debug("Returning container EntityManager proxy {} instead of EntityManager {}", proxy, original);
        return proxy;
    }

    /**
     * @param persistenceUnitName       persistent unit name
     * @param properties properties to create entity manager factory
     * @param original   entity manager factory
     * @return
     */
    public static EntityManagerFactory createEntityManagerFactoryProxy(String persistenceUnitName, Map properties,
                                                                                EntityManagerFactory original) {
        EntityManagerFactoryWrapper wrapper = EntityManagerFactoryWrapper.getWrapper(persistenceUnitName);
        EntityManagerFactory proxy = wrapper.proxy(original, persistenceUnitName, null, properties);

        initPlugin(original);

        LOGGER.debug("Returning EntityManager proxy {} instead of EntityManager {}", proxy, original);
        return proxy;
    }

    // call initializePlugin and setup version and EJB flag
    private static void initPlugin(EntityManagerFactory original) {
        ClassLoader appClassLoader = original.getClass().getClassLoader();

        String version = Version.getVersionString();

        PluginManagerInvoker.callInitializePlugin(HibernatePlugin.class, appClassLoader);
        PluginManagerInvoker.callPluginMethod(HibernatePlugin.class, appClassLoader,
                "hibernateInitialized",
                new Class[]{String.class, Boolean.class},
                new Object[]{version, true});

    }
}
