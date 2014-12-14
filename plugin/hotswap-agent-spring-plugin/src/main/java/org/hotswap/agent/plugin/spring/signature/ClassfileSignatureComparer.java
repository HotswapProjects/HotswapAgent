package org.hotswap.agent.plugin.spring.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Checks if a Signature of a Class has changed enough to necessitate a Spring reload.
 * 
 * @author Erki Ehtla
 * 
 */
public class ClassfileSignatureComparer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ClassfileSignatureComparer.class);
	
	/**
	 * @param clazz old Class definition
	 * @param cp ClassPool which should contain the new/compared definition
	 * @return is signature different
	 */
	public static boolean isPoolClassDifferent(Class<?> clazz, ClassPool cp) {
		String ctClassString;
		try {
			ctClassString = CtClassSignature.get(cp.get(clazz.getName()));
		} catch (NotFoundException | ClassNotFoundException e) {
			LOGGER.error("Error reading siganture", e);
			return false;
		}
		String string = JavaClassSignature.get(clazz);
		return !string.equals(ctClassString);
	}
	/**
	 * @param clazz old Class definition
	 * @param cp ClassPool which should contain the new/compared definition
	 * @return is signature different
	 */
	public static boolean isPoolClassDifferent(Class<?> clazz, ClassLoader cp) {
		String javaClassString;
		try {
			javaClassString = JavaClassSignature.get(cp.loadClass(clazz.getName()));
		} catch (ClassNotFoundException e) {
			LOGGER.error("Error reading siganture", e);
			return false;
		}
		return !JavaClassSignature.get(clazz).equals(javaClassString);
	}
}
