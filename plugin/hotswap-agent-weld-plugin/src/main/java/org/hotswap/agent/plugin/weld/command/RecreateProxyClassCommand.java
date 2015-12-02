package org.hotswap.agent.plugin.weld.command;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * RecreateProxyClassCommand
 *
 * @author Vladimir Dvorak
 */
public class RecreateProxyClassCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);

    private ClassLoader appClassLoader;
    private Map<Object, Object> registeredBeans;
    private String className;

    public RecreateProxyClassCommand(ClassLoader appClassLoader, Map<Object, Object> registeredProxiedBeans, String className) {
        this.appClassLoader = appClassLoader;
        this.registeredBeans = registeredProxiedBeans;
        this.className = className;
    }

    @Override
    public void executeCommand() {
        synchronized (registeredBeans) {
            doRecreateProxyFactory();
        }
    }

    private void doRecreateProxyFactory() {
        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(appClassLoader);

            Class<?> cls = appClassLoader.loadClass(className);
            // load class to avoid class not found exception

            for (Entry<Object, Object> entry : registeredBeans.entrySet()) {
                Bean<?> bean = (Bean<?>) entry.getKey();
                Set<Type> types = bean.getTypes();
                if (types.contains(cls)) {
                    Class<?> proxyFactoryClass = appClassLoader.loadClass("org.jboss.weld.bean.proxy.ProxyFactory");
                    Object proxyFactory = entry.getValue();
                    LOGGER.debug("Recreate proxyClass {} for bean class {}.", cls.getName(), bean.getClass());
                    ReflectionHelper.invoke(proxyFactory,
                            proxyFactoryClass, "__recreateProxyClass",
                            new Class[] {});
                }
            }

        } catch (ClassNotFoundException e) {
            LOGGER.error("isBdaRegistered() exception {}.", e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

}
