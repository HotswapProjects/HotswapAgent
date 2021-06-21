package org.hotswap.agent.plugin.ibatis;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.ibatis.IBatisConfigurationHandler;

/**
 * Reload IBatis configuration after entity change.
 * 
 * @author muwaiwai
 * @date 2021-6-18
 */
@Plugin(name = "IBatis",
        description = "Reload IBatis configuration after configuration create/change.",
        testedVersions = {"All between 2.3.4"},
        expectedVersions = {"2.3.4" },
        supportClass = { IBatisTransformers.class })
public class IBatisPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(IBatisPlugin.class);

    @Init
    private Scheduler scheduler;

    @Init
    private ClassLoader appClassLoader;

    private Set<String> configFileSet = new HashSet<>();
    
    private Command reloadIbatisConfigurationCommand =
            new ReflectionCommand(this, IBatisConfigurationHandler.class.getName(), "refresh");

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("IBatis plugin initialized.");
    }

    public void registConfigFile(String configFiles){
    	String[]paths=configFiles.split("\n");
    	for(String ph:paths) {
    		configFileSet.add(ph);
    		LOGGER.debug("IBatis config file registered : {}", ph);
    	}
    }

    @OnResourceFileEvent(path="/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListenersModify(URL url) {
    	if(configFileSet.contains(url.getPath())) {
    		LOGGER.info("IBatis config file changed : {}", url.getPath());
    		scheduler.scheduleCommand(reloadIbatisConfigurationCommand, 500);
    	}
    }
}
