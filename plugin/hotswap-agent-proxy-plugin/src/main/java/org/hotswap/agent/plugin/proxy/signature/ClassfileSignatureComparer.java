package org.hotswap.agent.plugin.proxy.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Checks if a Signature of a Class has changed
 * 
 * @author Erki Ehtla
 * 
 */
public class ClassfileSignatureComparer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ClassfileSignatureComparer.class);
	
	private static boolean isPoolClassDifferent(Class<?> clazz, ClassPool cp) {
		String ctClassString;
		try {
			ctClassString = CtClassSignature.get(cp.get(clazz.getName()));
		} catch (NotFoundException e) {
			LOGGER.error("Error reading siganture", e);
			return false;
		}
		if (ctClassString == null)
			return false;
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	public static boolean isPoolClassOrParentDifferent(Class<?> clazz, ClassPool cp) {
		if (isPoolClassDifferent(clazz, cp))
			return true;
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			if (isPoolClassOrParentDifferent(superclass, cp)) {
				return true;
			}
		}
		Class<?>[] interfaces = clazz.getInterfaces();
		for (Class<?> interfaceClazz : interfaces) {
			if (isPoolClassOrParentDifferent(interfaceClazz, cp)) {
				return true;
			}
		}
		return false;
	}
}
