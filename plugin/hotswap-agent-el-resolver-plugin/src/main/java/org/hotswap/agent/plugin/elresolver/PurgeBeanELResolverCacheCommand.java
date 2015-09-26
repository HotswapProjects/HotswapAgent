package org.hotswap.agent.plugin.elresolver;

import java.lang.reflect.Method;
import java.util.Set;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Purge caches in registered class loaders. It calls __purgeClassCache(...) injected to BeanELResolver in ELResolverPlugin.
 *
 */
public class PurgeBeanELResolverCacheCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeBeanELResolverCacheCommand.class);

    private ClassLoader appClassLoader;

    private Set<Object> registeredBeanELResolvers;

    public PurgeBeanELResolverCacheCommand(ClassLoader appClassLoader, Set<Object> registeredBeanELResolvers)
    {
        this.appClassLoader = appClassLoader;
        this.registeredBeanELResolvers = registeredBeanELResolvers;
    }

    @Override
    public void executeCommand() {
        LOGGER.debug("Purging BeanELResolver cache.");
        try {
            Method beanElResolverMethod = resolveClass("javax.el.BeanELResolver")
                    .getDeclaredMethod(ELResolverPlugin.PURGE_CLASS_CACHE_METHOD_NAME, ClassLoader.class);

            for (Object registeredBeanELResolver : registeredBeanELResolvers) {
                beanElResolverMethod.invoke(registeredBeanELResolver, appClassLoader);
            }
        } catch (Exception e) {
            LOGGER.error("Error purging BeanELResolver cache.", e);
        }
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PurgeBeanELResolverCacheCommand that = (PurgeBeanELResolverCacheCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PurgeBeanELResolverCacheCommand{" + "appClassLoader=" + appClassLoader + '}';
    }
}
