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

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.jpa.proxy.EntityManagerFactoryProxy;


/**
 * Reload the hibernate configuration.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Jiri Bubnik
 */
public class Hibernate3JPARefreshCommands {
    
    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3JPARefreshCommands.class);

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    /**
     * Reload entity manager factory.
     */
    public static void reloadEntityManagerFactory() {
        LOGGER.debug("Refreshing hibernate configuration.");
        EntityManagerFactoryProxy.refreshProxiedFactories();
        LOGGER.reload("Hibernate EntityMangerFactory configuration refreshed.");
        reloadFlag = false;
    }
}
