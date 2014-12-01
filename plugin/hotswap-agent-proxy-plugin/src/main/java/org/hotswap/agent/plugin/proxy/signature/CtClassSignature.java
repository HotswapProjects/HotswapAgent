package org.hotswap.agent.plugin.proxy.signature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.javassist.bytecode.ExceptionsAttribute;
import org.hotswap.agent.javassist.bytecode.MethodInfo;

/**
 * String representation of a CtClass instance. Consists of a super class name(if not Object), methods (names, return
 * types, parameter types) and interface names ordered alphabetically.
 * 
 * @author Erki Ehtla
 * 
 */
public class CtClassSignature {
	public static String get(CtClass cc) {
		@SuppressWarnings("unchecked")
		List<MethodInfo> methods = cc.getClassFile().getMethods();
		List<String> strings = new ArrayList<>();
		for (MethodInfo method : methods) {
			if (method.getName().equals("<init>") || method.getName().equals("<clinit>")
					|| Modifier.isPrivate(method.getAccessFlags()) || Modifier.isStatic(method.getAccessFlags()))
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
	
	private static String getReturnType(String desc) {
		int i = desc.indexOf(')');
		return Descriptor.toString(desc.substring(i + 1));
	}
	
	private static String getMethodString(MethodInfo method) {
		return Modifier.toString(method.getAccessFlags()) + " " + getReturnType(method.getDescriptor()) + " "
				+ method.getName() + Descriptor.toString(method.getDescriptor())
				+ toString(method.getExceptionsAttribute()) + ";";
	}
	
	public static String toString(ExceptionsAttribute ea) {
		if (ea == null || ea.getExceptions() == null)
			return "[]";
		
		int iMax = ea.getExceptions().length - 1;
		if (iMax == -1)
			return "[]";
		
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			b.append("class " + ea.getExceptions()[i]);
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}
}
