package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

public class SpringChangedReloadCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlsChangedCommand.class);

    // unit test only
    private static AtomicLong waitingTaskCount = new AtomicLong(0);

    ClassLoader appClassLoader;
    long timestamps;

    public SpringChangedReloadCommand(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
        this.timestamps = System.currentTimeMillis();
        LOGGER.trace("SpringChangedReloadCommand created with timestamp '{}'", timestamps);
        waitingTaskCount.incrementAndGet();
    }

    @Override
    public void executeCommand() {
        // async call to avoid reload too much times
        try {
            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.reload.SpringChangedAgent", true, appClassLoader);
            Method method = clazz.getDeclaredMethod(
                    "reload", long.class);
            method.invoke(null, timestamps);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error invoking method", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        } finally {
            waitingTaskCount.decrementAndGet();
        }
    }

    // this is used by tests
    public static boolean isEmptyTask() {
        return waitingTaskCount.get() == 0;
    }
}
