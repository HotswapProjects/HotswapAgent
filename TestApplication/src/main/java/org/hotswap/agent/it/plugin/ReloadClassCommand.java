package org.hotswap.agent.it.plugin;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.Method;

/**
 * A command to merge multiple reload events into one execution and execute the logic in application classloader.
 */
public class ReloadClassCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReloadClassCommand.class);
    ClassLoader appClassLoader;
    String className;
    Object testEnityService;

    /**
     * @param appClassLoader   Usually you need the application classloader as a parameter - to know in which
     *                         classloader the class you want to call lives
     * @param className        reloaded className
     * @param testEnityService the target service registered with the plugin - not that we are still in the
     *                         plugin classloader and the service cannot be typed to it's class.
     */
    public ReloadClassCommand(ClassLoader appClassLoader, String className, Object testEnityService) {
        this.appClassLoader = appClassLoader;
        this.className = className;
        this.testEnityService = testEnityService;
    }

    @Override
    public void executeCommand() {
        try {
            // Now we have application classloader and the service on which to invoke the method, we can use
            // reflection directly
            // but for demonstration purpose we invoke a plugin class, that lives in the application classloader
            Method setExamplePluginResourceText = appClassLoader.loadClass(ReloadClassService.class.getName())
                    .getDeclaredMethod("classReloaded", String.class, Object.class);
            setExamplePluginResourceText.invoke(null, className, testEnityService);
        } catch (Exception e) {
            LOGGER.error("Error invoking {}.reload()", e, ReloadClassService.class.getName());
        }

    }

    /**
     * Use equals to group "similar commands". If multiple "equals" commands are scheduled during
     * the scheduler timeout, only the last command is executed. If you need information regarding
     * all merged commands and/or select which is executed, use MergeableCommand superclass.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReloadClassCommand that = (ReloadClassCommand) o;

        if (!className.equals(that.className)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return className.hashCode();
    }
}
