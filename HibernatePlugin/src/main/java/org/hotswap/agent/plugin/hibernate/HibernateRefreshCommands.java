package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate.proxy.EntityManagerFactoryProxy;
import org.hotswap.agent.plugin.hibernate.proxy.SessionFactoryProxy;


/**
 * Reload the hibernate configuration.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class HibernateRefreshCommands {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernateRefreshCommands.class);

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    public static void reloadEntityManagerFactory() {
        LOGGER.debug("Refreshing hibernate configuration.");
        EntityManagerFactoryProxy.refreshProxiedFactories();
        LOGGER.reload("Hibernate EntityMangerFactory configuration refreshed.");
        reloadFlag = false;
    }

    public static void reloadSessionFactory() {
        LOGGER.debug("Refreshing SessionFactory configuration.");
        SessionFactoryProxy.refreshProxiedFactories();
        LOGGER.reload("Hibernate SessionFactory configuration refreshed.");
        reloadFlag = false;
    }
}
