package org.hotswap.agent.plugin.hotswapcommons;

import java.lang.reflect.Field;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class FlushIntrospectorCommand.
 */
public class FlushIntrospectorCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(FlushIntrospectorCommand.class);

    ClassLoader classLoader;

    public FlushIntrospectorCommand(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void executeCommand() {
        LOGGER.debug("Clearing java.beans.ThreadGroupContext cache.");
        try {
            final Class<?> threadGroupCtxClazz = Class.forName("java.beans.ThreadGroupContext", true, classLoader);
            Field fldDeclaredMethodCache = java.beans.Introspector.class.getDeclaredField("declaredMethodCache");
            fldDeclaredMethodCache.setAccessible(true);
            Object declaredMethodCache = fldDeclaredMethodCache.get(null);

            synchronized (declaredMethodCache) {
                Field fldContexts = threadGroupCtxClazz.getDeclaredField("contexts");
                fldContexts.setAccessible(true);
                Object contexts = fldContexts.get(null);
                ReflectionHelper.invoke(contexts, contexts.getClass(), "__reinitialize", null);
                ReflectionHelper.invoke(declaredMethodCache, declaredMethodCache.getClass(), "clear", null);
            }

        } catch (Exception e) {
            LOGGER.error("executeCommand() exception {}.", e.getMessage());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FlushIntrospectorCommand that = (FlushIntrospectorCommand) o;
        if (!classLoader.equals(that.classLoader)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return classLoader.hashCode();
    }

    @Override
    public String toString() {
        return "BeanClassRefreshCommand{classLoader=" + classLoader + '}';
    }
}
