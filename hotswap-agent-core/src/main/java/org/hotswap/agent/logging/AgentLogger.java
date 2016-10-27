package org.hotswap.agent.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * Create custom simple logging mechanism.
 * <p/>
 * Instead of java.util.logging because many frameworks and APP servers will complicate/override settings.
 *
 * @author Jiri Bubnik
 */
public class AgentLogger {

    /**
     * Get logger for a class
     *
     * @param clazz class to log
     * @return logger
     */
    public static AgentLogger getLogger(Class clazz) {
        return new AgentLogger(clazz);
    }

    private static Map<String, Level> currentLevels = new HashMap<String, Level>();

    public static void setLevel(String classPrefix, Level level) {
        currentLevels.put(classPrefix, level);
    }

    private static Level rootLevel = Level.INFO;

    public static void setLevel(Level level) {
        rootLevel = level;
    }

    private static AgentLoggerHandler handler = new AgentLoggerHandler();

    public static void setHandler(AgentLoggerHandler handler) {
        AgentLogger.handler = handler;
    }

    public static AgentLoggerHandler getHandler() {
        return handler;
    }

    public static void setDateTimeFormat(String dateTimeFormat) {
        handler.setDateTimeFormat(dateTimeFormat);
    }

    /**
     * Standard logging levels.
     */
    public enum Level {
        ERROR,
        RELOAD,
        WARNING,
        INFO,
        DEBUG,
        TRACE
    }

    private Class clazz;

    private AgentLogger(Class clazz) {
        this.clazz = clazz;
    }


    public boolean isLevelEnabled(Level level) {
        Level classLevel = rootLevel;

        String className = clazz.getName();
        String longestPrefix = "";
        for (String classPrefix : currentLevels.keySet()) {
            if (className.startsWith(classPrefix)) {
                if (classPrefix.length() > longestPrefix.length()) {
                    longestPrefix = classPrefix;
                    classLevel = currentLevels.get(classPrefix);
                }
            }
        }

        // iterate levels in order from most serious. If classLevel is first, it preciedes required level and log is disabled
        for (Level l : Level.values()) {
            if (l == level)
                return true;
            if (l == classLevel)
                return false;
        }

        throw new IllegalArgumentException("Should not happen.");
    }

    public void log(Level level, String message, Throwable throwable, Object... args) {
        if (isLevelEnabled(level))
            handler.print(clazz, level, message, throwable, args);
    }

    public void log(Level level, String message, Object... args) {
        log(level, message, null, args);
    }

    public void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    public void error(String message, Throwable throwable, Object... args) {
        log(Level.ERROR, message, throwable, args);
    }

    public void reload(String message, Object... args) {
        log(Level.RELOAD, message, args);
    }

    public void reload(String message, Throwable throwable, Object... args) {
        log(Level.RELOAD, message, throwable, args);
    }

    public void warning(String message, Object... args) {
        log(Level.WARNING, message, args);
    }

    public void warning(String message, Throwable throwable, Object... args) {
        log(Level.WARNING, message, throwable, args);
    }

    public void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    public void info(String message, Throwable throwable, Object... args) {
        log(Level.INFO, message, throwable, args);
    }

    public void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    public void debug(String message, Throwable throwable, Object... args) {
        log(Level.DEBUG, message, throwable, args);
    }

    public void trace(String message, Object... args) {
        log(Level.TRACE, message, args);
    }

    public void trace(String message, Throwable throwable, Object... args) {
        log(Level.TRACE, message, throwable, args);
    }

    public boolean isDebugEnabled() {
        return isLevelEnabled(Level.DEBUG);
    }

    public boolean isWarnEnabled() {
        return isLevelEnabled(Level.WARNING);
    }
}
