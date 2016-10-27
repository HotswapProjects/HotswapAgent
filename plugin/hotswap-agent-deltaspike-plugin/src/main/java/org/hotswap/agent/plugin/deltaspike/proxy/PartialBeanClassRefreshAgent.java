package org.hotswap.agent.plugin.deltaspike.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.enterprise.inject.spi.BeanManager;

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
                Class<?> targetClass = (Class) ReflectionHelper.get(lifecycle, "targetClass");
                PartialBeanProxyFactory proxyFactory = PartialBeanProxyFactory.getInstance();
                try {
                    // Deltaspike 1.5
                    Method m3 = PartialBeanProxyFactory.class.getMethod("getProxyClass", new Class[] { BeanManager.class, Class.class, Class.class} );
                    Class<? extends InvocationHandler> delegateInvocationHandlerClass = (Class) ReflectionHelper.get(lifecycle, "delegateInvocationHandlerClass");
                    m3.invoke(proxyFactory, new Object[] {BeanManagerProvider.getInstance().getBeanManager(), targetClass, delegateInvocationHandlerClass} );
                } catch (NoSuchMethodException e) {
                    // Deltaspike 1.7
                    Method m2 = PartialBeanProxyFactory.class.getMethod("getProxyClass", new Class[] { BeanManager.class, Class.class } );
                    m2.invoke(proxyFactory, new Object[] {BeanManagerProvider.getInstance().getBeanManager(), targetClass} );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Deltaspike proxy redefinition failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }

    }
}
