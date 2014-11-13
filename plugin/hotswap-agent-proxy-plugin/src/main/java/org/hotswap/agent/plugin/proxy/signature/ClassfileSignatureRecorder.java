package org.hotswap.agent.plugin.proxy.signature;

import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;

/**
 * @author Erki Ehtla
 * 
 */
public class ClassfileSignatureRecorder {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ClassfileSignatureChecker.class);
	
	private static boolean hasClassChanged(Class<?> clazz) {
		String ctClassString;
		try {
			ctClassString = CtClassSignature.get(ProxyTransformationUtils.getClassPool().get(clazz.getName()));
		} catch (NotFoundException e) {
			LOGGER.error("Error reading siganture", e);
			return false;
		}
		if (ctClassString == null)
			return false;
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	public static boolean hasSuperClassOrInterfaceChanged(Class<?> clazz) {
		if (hasClassChanged(clazz))
			return true;
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			if (ClassfileSignatureChecker.hasSuperClassOrInterfaceChanged(superclass)) {
				return true;
			}
		}
		Class<?>[] interfaces = clazz.getInterfaces();
		for (Class<?> interfaceClazz : interfaces) {
			if (ClassfileSignatureChecker.hasSuperClassOrInterfaceChanged(interfaceClazz)) {
				return true;
			}
		}
		return false;
	}
}
