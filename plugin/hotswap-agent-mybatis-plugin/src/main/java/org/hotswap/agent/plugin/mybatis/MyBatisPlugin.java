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
package org.hotswap.agent.plugin.mybatis;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;

/**
 * Reload MyBatis configuration after entity create/change.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "MyBatis",
        description = "Reload MyBatis configuration after configuration create/change.",
        testedVersions = {"All between 3.5.16"},
        expectedVersions = {"3.5.16"},
        supportClass = {MyBatisTransformers.class})
public class MyBatisPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Map<String, Object> configurationMap = new HashMap<>();

    Map<String, Object> configurationFolder = new HashMap<>();


    public static Set<Object> mybatisPlusProperties = new HashSet<>();

    public static Map<Object,Object> mybatisSessionBeanToProperties = new HashMap<>();

    public static Set<Object> mybatisProperties = new HashSet<>();

    Command reloadConfigurationCommand =
            new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "reloadConfiguration");

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("MyBatis plugin initialized.");
    }

    public void registerConfigurationFile(String configFile, Object configObject) {
        if (configFile != null && !configurationMap.containsKey(configFile)) {
            LOGGER.debug("MyBatisPlugin - configuration file registered : {}", configFile);
            configurationMap.put(configFile, configObject);
        }
        try {
            File file = new File(configFile);
            String absolutePath = file.getParentFile().getAbsolutePath();
            configurationFolder.put(absolutePath, configObject);
        }catch (Exception eee){
            LOGGER.info("Register mybatis configuration file folder error config file is:"+configFile,eee);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        File file = Paths.get(url.toURI()).toFile();
        if (configurationMap.containsKey(file.getAbsolutePath())) {
            refresh(500);
        } else {
            File parentFile = file.getParentFile();
            if(configurationFolder.containsKey(parentFile.getAbsolutePath())){
                refresh(500);
            }
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void registerClassListeners(Class<?> clazz) {
        if (ConfigurationProxy.isMybatisEntity(clazz) || SpringMybatisConfigurationProxy.isMybatisEntity(clazz)) {
            LOGGER.trace("Scheduling Mybatis reload for class '{}' in classLoader {}", clazz, appClassLoader);
            refresh(500);
        }
    }

    // reload the configuration - schedule a command to run in the application classloader and merge
    // duplicate commands.
    private void refresh(int timeout) {
        scheduler.scheduleCommand(reloadConfigurationCommand, timeout);
    }

}
