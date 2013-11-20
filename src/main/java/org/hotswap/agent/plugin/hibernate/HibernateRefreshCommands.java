package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.logging.AgentLogger;


/**
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class HibernateRefreshCommands {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernateRefreshCommands.class);

    public void reloadEntityManagerFactory() {
        EntityManagerFactoryWrapper.refreshProxiedFactories();
        LOGGER.reload("Hibernate EntityMangerFactory configuration refreshed.");
    }

    public void reloadSessionFactory() {
        SessionFactoryWrapper.refreshProxiedFactories();
    }
}
