package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Creates a new bytecode for a Java proxy. Changed Classes have to be already loaded in the App classloader.
 *
 * @author Erki Ehtla
 *
 */
public class JavaProxyBytecodeGenerator implements ProxyBytecodeGenerator {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JavaProxyBytecodeGenerator.class);

    private Class<?> classBeingRedefined;

    public JavaProxyBytecodeGenerator(Class<?> classBeingRedefined) {
        super();
        this.classBeingRedefined = classBeingRedefined;
    }

    public byte[] generate() throws Exception {
        Class<?> proxyGeneratorClass = null;

        try {
            proxyGeneratorClass = getClass().getClassLoader().loadClass("sun.misc.ProxyGenerator");
        } catch (ClassNotFoundException e) {
            try {
                // java9
                proxyGeneratorClass = getClass().getClassLoader().loadClass("java.lang.reflect.ProxyGenerator");
            } catch (ClassNotFoundException ex) {
                LOGGER.error("Unable to loadClass ProxyGenerator!");
                return null;
            }
        }

        return (byte[]) ReflectionHelper.invoke(null, proxyGeneratorClass, "generateProxyClass", new Class[] {String.class, Class[].class },
                classBeingRedefined.getName(), classBeingRedefined.getInterfaces());
    }
}
