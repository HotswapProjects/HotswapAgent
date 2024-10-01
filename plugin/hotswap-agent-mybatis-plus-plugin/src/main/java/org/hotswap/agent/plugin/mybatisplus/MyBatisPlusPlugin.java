/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.mybatisplus;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisplus.proxy.ConfigurationPlusProxy;
import org.hotswap.agent.plugin.mybatisplus.proxy.SpringMybatisPlusConfigurationProxy;
import org.hotswap.agent.plugin.mybatisplus.transformers.MyBatisPlusTransformers;

/**
 * Reload MyBatis configuration after entity create/change.
 */
@Plugin(name = "MyBatisPlus",
        description = "Reload MyBatis Plus configuration after configuration create/change.",
        testedVersions = {"All versions between 3.2.0 and 3.5.7"},
        expectedVersions = {"3.5.7"},
        supportClass = {MyBatisPlusTransformers.class})
public class MyBatisPlusPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlusPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Map<String, Object> configurationMap = new HashMap<>();

    Command reloadConfigurationCommand =
            new ReflectionCommand(this, MyBatisPlusRefreshCommands.class.getName(), "reloadConfiguration");

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("MyBatisPlus plugin initialized.");
    }

    public void registerConfigurationFile(String configFile, Object configObject) {
        if (configFile != null && !configurationMap.containsKey(configFile)) {
            LOGGER.debug("MyBatisPlugin - configuration file registered : {}", configFile);
            configurationMap.put(configFile, configObject);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        for (String s : configurationMap.keySet()) {
            LOGGER.trace(s);
        }
        if (configurationMap.containsKey(Paths.get(url.toURI()).toFile().getAbsolutePath())) {
            refresh(500);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void registerClassListeners(Class<?> clazz) {
        LOGGER.debug("Ready to schedule MybatisPlus reload for class '{}' in classLoader {}", clazz, appClassLoader);
        if (ConfigurationPlusProxy.isMybatisEntity(clazz) || SpringMybatisPlusConfigurationProxy.isMybatisPlusEntity(clazz)) {
            LOGGER.debug("Scheduling MybatisPlus reload for class '{}' in classLoader {}", clazz, appClassLoader);
            refresh(500);
        }
    }

    // reload the configuration - schedule a command to run in the application classloader and merge
    // duplicate commands.
    private void refresh(int timeout) {
        scheduler.scheduleCommand(reloadConfigurationCommand, timeout);
    }

}
