/*
 * Copyright 2013-2019 the HotswapAgent authors.
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
package org.hotswap.agent.config;

import static java.lang.Boolean.parseBoolean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Properties;

import org.hotswap.agent.logging.AgentLogger;

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
    public static AgentLogger.Level getLevel(String property, String levelName) {
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
