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
package org.hotswap.agent.plugin.mybatis;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;


/**
 * Reload the MyBatis configuration.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Vladimir Dvorak
 */
public class MyBatisRefreshCommands {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisRefreshCommands.class);

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    public static void reloadConfiguration() {
        LOGGER.debug("Refreshing MyBatis configuration.");
        ConfigurationProxy.refreshProxiedConfigurations();
        SpringMybatisConfigurationProxy.refreshProxiedConfigurations();
        LOGGER.reload("MyBatis configuration refreshed.");
        reloadFlag = false;
    }
}
