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
package org.hotswap.agent.plugin.hibernate3.jpa;

import org.hibernate.Version;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.jpa.proxy.EntityManagerFactoryProxy;
import org.hotswap.agent.util.PluginManagerInvoker;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper to create a proxy for entity manager factory.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class Hibernate3JPAHelper {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3JPAHelper.class);

    /** The wrapped persistence unit names. */
    // each persistence unit should be wrapped only once
    static Set<String> wrappedPersistenceUnitNames = new HashSet<>();

    /**
     * Creates the container entity manager factory proxy.
     *
     * @param info            persistent unit definition
     * @param properties            properties to create entity manager factory
     * @param original            entity manager factory
     * @return proxy of entity manager
     */
    public static EntityManagerFactory createContainerEntityManagerFactoryProxy(PersistenceUnitInfo info,
            Map<?,?> properties, EntityManagerFactory original) {
        // ensure only once
        if (wrappedPersistenceUnitNames.contains(info.getPersistenceUnitName())){
            return original;
        }
        wrappedPersistenceUnitNames.add(info.getPersistenceUnitName());

        EntityManagerFactoryProxy wrapper = EntityManagerFactoryProxy.getWrapper(info.getPersistenceUnitName());
        EntityManagerFactory proxy = wrapper.proxy(original, info.getPersistenceUnitName(), info, properties);

        initPlugin(original);

        LOGGER.debug("Returning container EntityManager proxy {} instead of EntityManager {}", proxy.getClass(),
                original);
        return proxy;
    }

    /**
     * Creates the entity manager factory proxy.
     *
     * @param persistenceUnitName            persistent unit name
     * @param properties            properties to create entity manager factory
     * @param original            entity manager factory
     * @return proxy of entity manager
     */
    public static EntityManagerFactory createEntityManagerFactoryProxy(String persistenceUnitName, Map<?,?> properties,
            EntityManagerFactory original) {
        // ensure only once
        if (wrappedPersistenceUnitNames.contains(persistenceUnitName)){
            return original;
        }
        wrappedPersistenceUnitNames.add(persistenceUnitName);

        EntityManagerFactoryProxy wrapper = EntityManagerFactoryProxy.getWrapper(persistenceUnitName);
        EntityManagerFactory proxy = wrapper.proxy(original, persistenceUnitName, null, properties);

        initPlugin(original);

        LOGGER.debug("Returning EntityManager proxy {} instead of EntityManager {}", proxy.getClass(), original);
        return proxy;
    }

    /**
     * Inits the plugin.
     *
     * @param original the original
     */
    // call initializePlugin and setup version and EJB flag
    private static void initPlugin(EntityManagerFactory original) {
        ClassLoader appClassLoader = original.getClass().getClassLoader();

        String version = Version.getVersionString();

        PluginManagerInvoker.callInitializePlugin(Hibernate3JPAPlugin.class, appClassLoader);
        PluginManagerInvoker.callPluginMethod(Hibernate3JPAPlugin.class, appClassLoader, "init", new Class[] { String.class, Boolean.class }, new Object[] { version, true });

    }
}
