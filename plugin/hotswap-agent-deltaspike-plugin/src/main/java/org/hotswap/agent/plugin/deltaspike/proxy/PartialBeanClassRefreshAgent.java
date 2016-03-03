package org.hotswap.agent.plugin.deltaspike.proxy;

import java.lang.reflect.InvocationHandler;

import org.apache.deltaspike.core.api.provider.BeanManagerProvider;
import org.apache.deltaspike.partialbean.impl.PartialBeanProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

public class PartialBeanClassRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PartialBeanClassRefreshAgent.class);

    public static void refreshPartialBeanClass(ClassLoader classLoader, Object partialBean) {
        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
        ProxyClassLoadingDelegate.beginProxyRegeneration();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            Object lifecycle = (Object) ReflectionHelper.get(partialBean, "lifecycle");
            if (lifecycle != null) {
                Class<? extends InvocationHandler> delegateInvocationHandlerClass = (Class) ReflectionHelper.get(lifecycle, "delegateInvocationHandlerClass");
                Class<?> targetClass = (Class) ReflectionHelper.get(lifecycle, "targetClass");
                PartialBeanProxyFactory proxyFactory = PartialBeanProxyFactory.getInstance();
                proxyFactory.getProxyClass(BeanManagerProvider.getInstance().getBeanManager(), targetClass, delegateInvocationHandlerClass);
            }
        } catch (Exception e) {
            LOGGER.error("Deltaspike proxy redefinition failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }

    }
}
