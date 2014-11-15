/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.Descriptor;

/**
 * String representation of a Java Class instance
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaClassSignature {
	
	public static String get(Class<?> cc) {
		List<String> methodStrings = new ArrayList<>();
		for (Method method : cc.getDeclaredMethods()) {
			methodStrings.add(getMethodString(method));
		}
		Collections.sort(methodStrings);
		StringBuilder strBuilder = new StringBuilder();
		for (String methodString : methodStrings) {
			strBuilder.append(methodString);
		}
		return strBuilder.toString();
	}
	
	private static String getMethodString(Method method) {
		return Modifier.toString(method.getModifiers()) + " " + getName(method.getReturnType()) + " "
				+ method.getName() + getParams(method.getParameterTypes()) + ";";
	}
	
	private static String getParams(Class<?>[] parameterTypes) {
		StringBuilder strB = new StringBuilder("(");
		boolean first = true;
		for (Class<?> ctClass : parameterTypes) {
			if (!first)
				strB.append(",");
			else
				first = false;
			strB.append(getName(ctClass));
		}
		strB.append(")");
		return strB.toString();
	}
	
	private static String getName(Class<?> ctClass) {
		if (ctClass.isArray())
			return Descriptor.toString(ctClass.getName());
		else
			return ctClass.getName();
	}
}
