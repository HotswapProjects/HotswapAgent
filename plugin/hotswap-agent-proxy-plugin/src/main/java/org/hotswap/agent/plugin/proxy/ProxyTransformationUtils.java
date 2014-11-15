package org.hotswap.agent.plugin.proxy;

import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;

/**
 * 
 * @author Erki Ehtla
 * 
 */
public class ProxyTransformationUtils {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyTransformationUtils.class);
	private static ConcurrentHashMap<ClassLoader, ClassPool> classPoolMap = new ConcurrentHashMap<>(2);
	
	/**
	 * Creates one ClassPool per ClassLoader and caches it
	 * 
	 * @param classLoader
	 * @return
	 */
	public static ClassPool getClassPool(ClassLoader classLoader) {
		ClassPool classPool = classPoolMap.get(classLoader);
		if (classPool == null) {
			synchronized (classPoolMap) {
				classPool = classPoolMap.get(classLoader);
				if (classPool == null) {
					classPool = createClassPool(classLoader);
					classPoolMap.put(classLoader, classPool);
				}
			}
		}
		return classPool;
	}
	
	/**
	 * Creates a ClassPool with supplied ClassLoader
	 * 
	 * @param classLoader
	 * @return
	 */
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
