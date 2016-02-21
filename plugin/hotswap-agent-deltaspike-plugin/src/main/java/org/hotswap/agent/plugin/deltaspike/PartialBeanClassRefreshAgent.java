package org.hotswap.agent.plugin.deltaspike;

import java.lang.reflect.InvocationHandler;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

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
                BeanManager beanManager = CDI.current().getBeanManager();
                proxyFactory.getProxyClass(beanManager, targetClass, delegateInvocationHandlerClass);
            }
        } catch (Exception e) {
            LOGGER.error("Deltaspike proxy redefinition failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }

    }
}
