package org.hotswap.agent.config;

import org.hotswap.agent.logging.AgentLogger;

import static java.lang.Boolean.parseBoolean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Properties;

/**
 * Configure LOG level and handler according to properties.
 */
public class LogConfigurationHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(LogConfigurationHelper.class);

    public static final String LOGGER_PREFIX = "LOGGER";
    public static final String DATETIME_FORMAT = "LOGGER_DATETIME_FORMAT";
    private static final String LOGFILE = "LOGFILE";
    private static final String LOGFILE_APPEND = "LOGFILE.append";

    /**
     * Search properties for prefix LOGGER and set level for package in format:
     * LOGGER.my.package=LEVEL
     *
     * @param properties properties
     */
    public static void configureLog(Properties properties) {
        for (String property : properties.stringPropertyNames()) {
            if (property.startsWith(LOGGER_PREFIX)) {
                if (property.startsWith(DATETIME_FORMAT)) {
                    String dateTimeFormat = properties.getProperty(DATETIME_FORMAT);
                    if (dateTimeFormat != null && !dateTimeFormat.isEmpty()) {
                        AgentLogger.setDateTimeFormat(dateTimeFormat);
                    }
                } else {
                    String classPrefix = getClassPrefix(property);
                    AgentLogger.Level level = getLevel(property, properties.getProperty(property));

                    if (level != null) {
                        if (classPrefix == null)
                            AgentLogger.setLevel(level);
                        else
                            AgentLogger.setLevel(classPrefix, level);
                    }
                }
            } else if (property.equals(LOGFILE)) {
                String logfile = properties.getProperty(LOGFILE);
                boolean append = parseBoolean(properties.getProperty(LOGFILE_APPEND, "false"));
                try {
                    PrintStream ps = new PrintStream(new FileOutputStream(new File(logfile), append));
                    AgentLogger.getHandler().setPrintStream(ps);
                } catch (FileNotFoundException e) {
                    LOGGER.error("Invalid configuration property {} value '{}'. Unable to create/open the file.",
                            e, LOGFILE, logfile);
                }
            }
        }
    }

    // resolve level from enum
    private static AgentLogger.Level getLevel(String property, String levelName) {
        try {
            return AgentLogger.Level.valueOf(levelName.toUpperCase(Locale.ENGLISH));
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
            return property.substring(LOGGER_PREFIX.length() + 1);
        }
    }
}
