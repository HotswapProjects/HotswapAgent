package org.hotswap.agent.plugin.owb.command;

import java.security.ProtectionDomain;

import org.hotswap.agent.javassist.bytecode.ClassFile;

/**
 * The CDI proxyFactory has its class loading tasks delegated to this class, which can then have some magic applied
 * to make weld think that the class has not been loaded yet.
 *
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

    public static Class<?> loadClass(final ClassLoader classLoader, final String className) throws ClassNotFoundException {
        if (MAGIC_IN_PROGRESS.get()) {
            throw new ClassNotFoundException("HotswapAgent");
        }
        return classLoader.loadClass(className);
    }

    public static Class<?> toClass(ClassFile ct, ClassLoader loader, ProtectionDomain domain) {
        /*
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(ct.getName());
                try {
                    ByteArrayDataOutputStream out = new ByteArrayDataOutputStream();
                    ct.write(out);
                    Map<Class<?>, byte[]> reloadMap = new HashMap<Class<?>, byte[]>();
                    reloadMap.put(originalProxyClass, out.getBytes());
                    // TODO : is this standard way how to reload class?
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                //it has not actually been loaded yet
                return ClassFileUtils.toClass(ct, loader, domain);
            }
        }
        return ClassFileUtils.toClass(ct, loader, domain);
        */
        return null;
    }

}
