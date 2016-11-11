package org.hotswap.agent.plugin.elresolver;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Flushe JBoss ReflectionUtil caches
 */
public class PurgeJbossReflectionUtil  extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeBeanELResolverCacheCommand.class);

    private ClassLoader appClassLoader;

    public PurgeJbossReflectionUtil(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
    }

    @Override
    public void executeCommand() {
        try {
            LOGGER.debug("Flushing Jboss ReflectionUtil");
            Class<?> reflectionUtilClass = appClassLoader.loadClass("org.jboss.el.util.ReflectionUtil");
            Object cache = ReflectionHelper.get(null, reflectionUtilClass, "methodCache");
            ReflectionHelper.invoke(cache, cache.getClass(), "clear", null);
        } catch (Exception e) {
            LOGGER.error("executeCommand() exception {}.", e.getMessage());
        }
    }

}
