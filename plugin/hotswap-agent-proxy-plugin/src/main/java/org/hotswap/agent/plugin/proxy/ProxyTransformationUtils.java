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
	
	public static ClassPool getClassPool() {
		return classPool;
	}
	
	public static ClassPool getOrCreateClassPool(ClassLoader classLoader) {
		if (classPool == null) {
			classPool = createClassPool(classLoader);
		}
		return classPool;
	}
	
	public static ClassPool createClassPool(ClassLoader classLoader) {
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		if (classLoader != null) {
			LOGGER.trace("Adding loader classpath " + classLoader);
			cp.appendClassPath(new LoaderClassPath(classLoader));
		}
		return cp;
	}
}
