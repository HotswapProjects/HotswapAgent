package org.hotswap.agent.plugin.proxy;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author Erki Ehtla
 * 
 */
public class ProxyTransformationUtils {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyTransformationUtils.class);
	private static ClassPool classPool;
	
	public static String getClassName(String name) {
		return name.replaceAll("/", ".");
	}
	
	public static ClassPool getClassPool() {
		if (classPool == null) {
			initClassPool(null);
		}
		return classPool;
	}
	
	public static void initClassPool(ClassLoader classLoader) {
		if (classPool == null) {
			classPool = new ClassPool();
			classPool.appendSystemPath();
			if (classLoader != null) {
				LOGGER.trace("Adding loader classpath " + classLoader);
				classPool.appendClassPath(new LoaderClassPath(classLoader));
			}
		}
	}
}
