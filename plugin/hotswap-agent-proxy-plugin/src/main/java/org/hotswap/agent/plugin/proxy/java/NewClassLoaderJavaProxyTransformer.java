package org.hotswap.agent.plugin.proxy.java;

import java.lang.instrument.IllegalClassFormatException;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ParentLastClassLoader;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureComparer;

/**
 * Proxy transformer for java.lang.reflect.Proxy. One-step process, uses CtClasses from the ClassPool.
 * 
 * @author Erki Ehtla
 * 
 */
public class NewClassLoaderJavaProxyTransformer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(NewClassLoaderJavaProxyTransformer.class);
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cc
	 *            CtClass from classfileBuffer
	 * @param cp
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws IllegalClassFormatException
	 */
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(final Class<?> classBeingRedefined, byte[] classfileBuffer, ClassLoader loader) {
		try {
			if (!isProxy(classBeingRedefined.getName())) {
				return classfileBuffer;
			}
			ParentLastClassLoader parentLastClassLoader = new ParentLastClassLoader(loader);
			Class<?>[] interfaces = classBeingRedefined.getInterfaces();
			Class<?>[] newInterfaces = new Class[interfaces.length];
			for (int i = 0; i < newInterfaces.length; i++) {
				newInterfaces[i] = parentLastClassLoader.loadClass(interfaces[i].getName());
			}
			if (!ClassfileSignatureComparer.isDifferent(interfaces, newInterfaces)) {
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
	
	public static boolean isProxy(String className) {
		return className.startsWith("com.sun.proxy.$Proxy");
	}
}