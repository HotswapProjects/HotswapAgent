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
	static Set<String> wrappedPersistenceUnitNames = new HashSet<String>();

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
