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
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	/**
	 * Checks if the CtClass or one of its parents signature differs from the one already loaded by Java.
	 * 
	 * @param clazz
	 * @param cp
	 * @return
	 */
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
	
	/**
	 * Class arrays need to be in the same order. Check if a signature of class differs from aonther. Useful for
	 * checking difference in different classloaders.
	 * 
	 * @param classesA
	 * @param classesB
	 * @return
	 */
	public static boolean isDifferent(Class<?>[] classesA, Class<?>[] classesB) {
		for (int i = 0; i < classesB.length; i++) {
			Class<?> class1 = classesA[i];
			Class<?> class2 = classesB[i];
			if (!JavaClassSignature.get(class1).equals(JavaClassSignature.get(class2))) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean isPoolClassDifferent(Class<?> clazz, ClassLoader cp) {
		String ctClassString;
		try {
			ctClassString = JavaClassSignature.get(cp.loadClass(clazz.getName()));
		} catch (ClassNotFoundException e) {
			LOGGER.error("Error reading siganture", e);
			return false;
		}
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	/**
	 * Checks if the Class or one of its parents signature differs from the one in the classloader.
	 * 
	 * @param clazz
	 * @param cp
	 * @return
	 */
	public static boolean isPoolClassOrParentDifferent(Class<?> clazz, ClassLoader cp) {
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
