/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.javassist.bytecode.MethodInfo;

/**
 * String representation of a CtClass instance
 * 
 * @author Erki Ehtla
 * 
 */
public class CtClassSignature {
	public static String get(CtClass cc) {
		List<MethodInfo> methods = (List<MethodInfo>) cc.getClassFile().getMethods();
		List<String> strings = new ArrayList<>();
		for (MethodInfo method : methods) {
			if (method.getName().equals("<init>") || method.getName().equals("<clinit>"))
				continue;
			strings.add(getMethodString(method));
		}
		for (String className : cc.getClassFile().getInterfaces()) {
			strings.add(className);
		}
		if (cc.getClassFile().getSuperclass() != null
				&& !cc.getClassFile().getSuperclass().equals(Object.class.getName()))
			strings.add(cc.getClassFile().getSuperclass());
		Collections.sort(strings);
		StringBuilder strBuilder = new StringBuilder();
		for (String methodString : strings) {
			strBuilder.append(methodString);
		}
		return strBuilder.toString();
	}
	
	public static String getReturnType(String desc) {
		int i = desc.indexOf(')');
		return Descriptor.toString(desc.substring(i + 1));
	}
	
	private static String getMethodString(MethodInfo method) {
		return Modifier.toString(method.getAccessFlags()) + " " + getReturnType(method.getDescriptor()) + " "
				+ method.getName() + Descriptor.toString(method.getDescriptor()) + ";";
	}
}
