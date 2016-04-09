package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig.ConfiguredBy;

/**
 * Workaround for not being able to use java8 interfaces with default methods... Oh well...
 * 
 * @author alpapad@gmail.com
 *
 */
public class ReInitializableHelper {
    
	private static AgentLogger LOGGER = AgentLogger.getLogger(ReInitializableHelper.class);

    
	public static void hotSwap(ReInitializable r){
		
		OverrideConfig o = r.getOverrideConfig();
		 r.reInitialize();
		switch(o.configuredBy) {
		case FILE:
			 r.configure(File.class.cast(o.config));
			break;
		case NONE:
			r.configure();
			break;
		case STRING:
			r.configure(String.class.cast(o.config));
			break;
		case URL:
			r.configure(URL.class.cast(o.config));
			break;
		case W3C:
			r.configure(org.w3c.dom.Document.class.cast(o.config));
			break;
		default:
			throw new RuntimeException("Don't know how to reconficure...");
		}
		for(Map.Entry<String, String> e: o.properties.entrySet()) {
			r.setProperty(e.getKey(), e.getValue());
		}
	}
	

	
	
	
	public static Configuration setProperty(ReInitializable r,String propertyName, String value) {
		LOGGER.debug("setProperty..................... key:" + propertyName + ", value:" + value);
    	r._setProperty( propertyName, value);
    	r.getOverrideConfig().properties.put(propertyName, value);
    	return (Configuration)r;
    }
    
	public static Configuration configure(ReInitializable r, String resource) throws HibernateException {
		LOGGER.debug("Configuring....................." + resource);
    	r._configure(resource);
    	r.getOverrideConfig().config = resource;
    	r.getOverrideConfig().configuredBy = ConfiguredBy.STRING;
    	r.getOverrideConfig().properties.clear();
    	return (Configuration)r;
    }
	public static Configuration configure(ReInitializable r, java.net.URL url) throws HibernateException {
		LOGGER.debug("Configuring....................." + url);
    	r._configure(url);
    	r.getOverrideConfig().config = url;
    	r.getOverrideConfig().configuredBy = ConfiguredBy.URL; 	
    	r.getOverrideConfig().properties.clear();
    	return (Configuration)r;
    }
    
	public static Configuration configure(ReInitializable r, java.io.File configFile) throws HibernateException{
    	System.err.println("Configuring....................." + configFile);
    	r._configure(configFile);
    	r.getOverrideConfig().properties.clear();
    	return (Configuration)r;
    }
    
	public static Configuration configure(ReInitializable r, org.w3c.dom.Document document) throws HibernateException {
		LOGGER.debug("Configuring....................." + document);
    	r._configure(document);
    	r.getOverrideConfig().config = document;
    	r.getOverrideConfig().configuredBy = ConfiguredBy.W3C;
    	r.getOverrideConfig().properties.clear();
    	return (Configuration)r;
    }
	
	public static Configuration configure(ReInitializable r) throws HibernateException{
		LOGGER.debug("Configuring..................... EMPTY..");
    	r._configure();
    	r.getOverrideConfig().config = null;
    	r.getOverrideConfig().configuredBy = ConfiguredBy.NONE;
    	r.getOverrideConfig().properties.clear();
    	return (Configuration)r;
    }
    
}
