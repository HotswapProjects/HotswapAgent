/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.NotFoundException;

/**
 * @author Erki Ehtla
 * 
 */
public class CtClassSignature {
	
	public static String get(CtClass cc) {
		StringBuilder strBuilder = new StringBuilder();
		for (CtMethod method : cc.getDeclaredMethods()) {
			strBuilder.append(getMethodString(method));
		}
		return strBuilder.toString();
	}
	
	private static String getMethodString(CtMethod method) {
		try {
			return Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " "
					+ method.getName() + getParams(method.getParameterTypes()) + ";";
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static String getParams(CtClass[] parameterTypes) {
		StringBuilder strB = new StringBuilder("(");
		for (CtClass ctClass : parameterTypes) {
			strB.append(ctClass.getName());
			strB.append(", ");
		}
		strB.append(")");
		return strB.toString();
	}
	
}
