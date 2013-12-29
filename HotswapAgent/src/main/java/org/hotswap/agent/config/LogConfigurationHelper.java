package org.hotswap.agent.config;

import org.hotswap.agent.logging.AgentLogger;

import java.util.Properties;

/**
 * Configure LOG level according to properties.
 */
public class LogConfigurationHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(LogConfigurationHelper.class);

    public static final String LOGGER_PREFIX = "LOGGER";

    /**
     * Search properties for prefix LOGGER and set level for package in format:
     *    LOGGER.my.package=LEVEL
     *
     * @param properties properties
     */
    public static void configureLog(Properties properties) {
        for (String property : properties.stringPropertyNames()) {
            if (property.startsWith(LOGGER_PREFIX)) {
                String classPrefix = getClassPrefix(property);
                AgentLogger.Level level = getLevel(property, properties.getProperty(property));

                if (level != null) {
                    if (classPrefix == null)
                        AgentLogger.setLevel(level);
                    else
                        AgentLogger.setLevel(classPrefix, level);
                }
            }
        }
    }

    // resolve level from enum
    private static AgentLogger.Level getLevel(String property, String levelName) {
        try {
            return AgentLogger.Level.valueOf(levelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid configuration value for property '{}'. Unknown LOG level '{}'.", property, levelName);
            return null;
        }
    }

    // get package name from logger
    private static String getClassPrefix(String property) {
        if (property.equals(LOGGER_PREFIX)) {
            return null;
        } else {
            return property.substring(LOGGER_PREFIX.length()+1);
        }
    }
}
