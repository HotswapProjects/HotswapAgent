package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.owb.OwbClassSignatureHelper;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class ProxyRefreshAgent.
 *
 * @author Vladimir Dvorak
 */
public class ProxyRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyRefreshAgent.class);

    /**
     * Recreate proxy classes, Called from BeanClassRefreshCommand.
     *
     * @param appClassLoader the application class loader
     * @param beanClassName the bean class name
     * @param oldSignatureForProxyCheck the old signature for proxy check
     * @throws IOException error working with classDefinition
     */
    public static void recreateProxy(ClassLoader appClassLoader, String beanClassName, String oldSignatureForProxyCheck) throws IOException {
        try {
            Class<?> beanClass = appClassLoader.loadClass(beanClassName);
            if (oldSignatureForProxyCheck != null) {
                String newClassSignature = OwbClassSignatureHelper.getSignatureForProxyClass(beanClass);
                if (newClassSignature != null && !newClassSignature.equals(oldSignatureForProxyCheck)) {
                    doRecreateProxy(appClassLoader, beanClass);
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Bean class '{}' not found.", beanClassName, e);
        }
    }

    private static void doRecreateProxy(ClassLoader appClassLoader, Class<?> beanClass) {

        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            ProxyClassLoadingDelegate.beginProxyRegeneration();
            Thread.currentThread().setContextClassLoader(appClassLoader);

            WebBeansContext wbc = WebBeansContext.currentInstance();
            NormalScopeProxyFactory proxyFactory = wbc.getNormalScopeProxyFactory();

            // Clear proxy class cache
            Map cachedProxyClasses = (Map) ReflectionHelper.get(proxyFactory, "cachedProxyClasses");
            Set<Bean<?>> beans = wbc.getBeanManagerImpl().getBeans(beanClass);
            if (beans != null) {
                boolean recreateIt = false;
                for (Bean<?> bean : beans) {
                    if (cachedProxyClasses.containsKey(bean)) {
                        cachedProxyClasses.remove(bean);
                        recreateIt = true;
                    }
                }
                if (recreateIt) {
                    proxyFactory.createProxyClass(appClassLoader, beanClass);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Proxy redefinition failed {}.", e, e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }
    }
}
