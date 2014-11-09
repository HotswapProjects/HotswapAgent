/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature;

import java.lang.reflect.Method;

import org.hotswap.agent.javassist.Modifier;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaClassSignature {
	
	public static String get(Class<?> cc) {
		StringBuilder strBuilder = new StringBuilder();
		for (Method method : cc.getDeclaredMethods()) {
			strBuilder.append(getMethodString(method));
		}
		return strBuilder.toString();
	}
	
	private static Object getMethodString(Method method) {
		return Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " "
				+ method.getName() + getParams(method.getParameterTypes()) + ";";
	}
	
	private static String getParams(Class<?>[] parameterTypes) {
		StringBuilder strB = new StringBuilder("(");
		for (Class<?> ctClass : parameterTypes) {
			strB.append(ctClass.getName());
			strB.append(", ");
		}
		strB.append(")");
		return strB.toString();
	}
	
}
