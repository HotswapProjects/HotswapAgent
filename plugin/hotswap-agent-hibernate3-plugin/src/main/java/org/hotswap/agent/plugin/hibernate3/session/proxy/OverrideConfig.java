package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plugin configuration
 * 
 * @author alpapad@gmail.com
 *
 */
public class OverrideConfig {

	public static enum ConfiguredBy {
		NONE,
		FILE,
		STRING,
		URL,
		W3C
	}
	
	public ConfiguredBy configuredBy = ConfiguredBy.NONE;
	
	public Object config = null;
	
	public Map<String,String> properties = new LinkedHashMap<>();
	
}
