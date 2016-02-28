package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ParentLastClassLoader;
import org.hotswap.agent.plugin.proxy.ProxyClassSignatureHelper;
import org.hotswap.agent.plugin.proxy.ProxyTransformer;

/**
 * Redefines Java proxy classes. One-step process. Uses Classes from a new classloader.
 *
 * @author Erki Ehtla
 *
 */
public class NewClassLoaderJavaProxyTransformer implements ProxyTransformer {
    /**
     *
     * @param classBeingRedefined
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param loader
     *            classloader of classBeingRedefined
     * @return classfileBuffer or new Proxy defition if there are signature changes
     */
    public NewClassLoaderJavaProxyTransformer(Class<?> classBeingRedefined, byte[] classfileBuffer, ClassLoader loader) {
        super();
        this.classBeingRedefined = classBeingRedefined;
        this.classfileBuffer = classfileBuffer;
        this.loader = loader;
    }

    private static AgentLogger LOGGER = AgentLogger.getLogger(NewClassLoaderJavaProxyTransformer.class);
    private final Class<?> classBeingRedefined;
    private final byte[] classfileBuffer;
    private final ClassLoader loader;

    /**
     *
     * @param classBeingRedefined
     * @param classfileBuffer
     *            new definition of Class<?>
     * @param loader
     *            classloader of classBeingRedefined
     * @return classfileBuffer or new Proxy defition if there are signature changes
     * @throws Exception
     */
    public static byte[] transform(final Class<?> classBeingRedefined, byte[] classfileBuffer, ClassLoader loader)
            throws Exception {
        return new NewClassLoaderJavaProxyTransformer(classBeingRedefined, classfileBuffer, loader).transformRedefine();
    }

    @Override
    public byte[] transformRedefine() throws Exception {
        try {
            ParentLastClassLoader parentLastClassLoader = new ParentLastClassLoader(loader);
            Class<?>[] interfaces = classBeingRedefined.getInterfaces();
            Class<?>[] newInterfaces = new Class[interfaces.length];
            for (int i = 0; i < newInterfaces.length; i++) {
                newInterfaces[i] = parentLastClassLoader.loadClass(interfaces[i].getName());
            }
            if (!ProxyClassSignatureHelper.isDifferent(interfaces, newInterfaces)) {
                return classfileBuffer;
            }

            Class<?> generatorClass = parentLastClassLoader.loadClass(ProxyGenerator.class.getName());
            byte[] generateProxyClass = (byte[]) generatorClass.getDeclaredMethod("generateProxyClass", String.class,
                    Class[].class).invoke(null, classBeingRedefined.getName(), newInterfaces);
            LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
            return generateProxyClass;
        } catch (Exception e) {
            LOGGER.error("Error transforming a Java reflect Proxy", e);
            return classfileBuffer;
        }
    }
}