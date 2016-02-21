package org.hotswap.agent.plugin.deltaspike;

import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.apache.deltaspike.proxy.impl.AsmProxyClassGenerator;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Delegates proxy loading to AsmProxyClassGenerator or PluginManager.getInstance().hotswap
 * @author Vladimir Dvorak
 */
public class ProxyClassLoadingDelegate {

    private static final ThreadLocal<Boolean> MAGIC_IN_PROGRESS = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static final void beginProxyRegeneration() {
        MAGIC_IN_PROGRESS.set(true);
    }

    public static final void endProxyRegeneration() {
        MAGIC_IN_PROGRESS.remove();
    }

    public static Class<?> tryToLoadClassForName(String proxyClassName, Class<?> targetClass) {
        if (MAGIC_IN_PROGRESS.get()) {
            return null;
        }
        return org.apache.deltaspike.core.util.ClassUtils.tryToLoadClassForName(proxyClassName, targetClass);
    }

    public static Class<?> loadClass(ClassLoader loader, String className, byte[] bytes, ProtectionDomain protectionDomain) {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(className);
                try {
                    Map<Class<?>, byte[]> reloadMap = new HashMap<Class<?>, byte[]>();
                    reloadMap.put(originalProxyClass, bytes);
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                //it has not actually been loaded yet
            }
        }
        try {
            return (Class<?>) ReflectionHelper.invoke(null, AsmProxyClassGenerator.class, "loadClass",
                    new Class[]{ClassLoader.class, String.class, byte[].class, ProtectionDomain.class},
                    loader, className, bytes, protectionDomain);
        } catch (Exception e) {
        }
        return null;
    }
}
