package org.hotswap.agent.plugin.proxy.signature.annot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.Descriptor;

/**
 * String representation of a Java Class instance. Consists of a super class name(if not Object), methods (names, return
 * types, parameter types) and interface names ordered alphabetica
 * 
 * @author Erki Ehtla
 * 
 */
public class CopyOfJavaClassSignature {
	
	public static String get(Class<?> cc) {
		List<String> strings = new ArrayList<>();
		for (Method method : cc.getDeclaredMethods()) {
			if (Modifier.isPrivate(method.getModifiers()))
				continue;
			strings.add(getMethodString(method));
		}
		
		for (Constructor<?> method : cc.getDeclaredConstructors()) {
			if (Modifier.isPrivate(method.getModifiers()))
				continue;
			strings.add(getConstructorString(method));
		}
		
		strings.add(Arrays.toString(sort(cc.getAnnotations())));
		for (Class<?> iClass : cc.getInterfaces()) {
			strings.add(iClass.getName());
		}
		if (cc.getSuperclass() != null && !cc.getSuperclass().getName().equals(Object.class.getName()))
			strings.add(cc.getSuperclass().getName());
		for (Field iClass : cc.getDeclaredFields()) {
			strings.add(iClass.getType().getName() + " " + iClass.getName()
					+ Arrays.toString(sort(iClass.getAnnotations())) + ";");
		}
		Collections.sort(strings);
		StringBuilder strBuilder = new StringBuilder();
		for (String methodString : strings) {
			strBuilder.append(methodString);
		}
		return strBuilder.toString();
	}
	
	private static Object[] sort(Object[] a) {
		
		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ObjectToStringComparator.INSTANCE);
		return a;
	}
	
	private static Object[][] sort(Object[][] a) {
		
		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ObjectToStringComparator.INSTANCE);
		for (Object[] objects : a) {
			Arrays.sort(objects, ObjectToStringComparator.INSTANCE);
		}
		return a;
	}
	
	private static String getConstructorString(Constructor<?> method) {
		return Modifier.toString(method.getModifiers()) + " " + method.getName()
				+ getParams(method.getParameterTypes()) + Arrays.toString(sort(method.getDeclaredAnnotations()))
				+ Arrays.deepToString(sort(method.getParameterAnnotations()))
				+ Arrays.toString(sort(method.getExceptionTypes())) + ";";
	}
	
	private static String getMethodString(Method method) {
		return Modifier.toString(method.getModifiers()) + " " + getName(method.getReturnType()) + " "
				+ method.getName() + getParams(method.getParameterTypes())
				+ Arrays.toString(sort(method.getDeclaredAnnotations()))
				+ Arrays.deepToString(sort(method.getParameterAnnotations()))
				+ Arrays.toString(sort(method.getExceptionTypes())) + ";";
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
