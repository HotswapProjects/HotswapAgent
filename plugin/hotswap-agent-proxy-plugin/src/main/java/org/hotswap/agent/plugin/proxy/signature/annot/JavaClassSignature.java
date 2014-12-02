package org.hotswap.agent.plugin.proxy.signature.annot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.bytecode.Descriptor;

/**
 * String representation of a Java Class instance. Consists of a super class name(if not Object), methods (names, return
 * types, parameter types) and interface names ordered alphabetica
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaClassSignature {
	
	public static String get(Class<?> cc) {
		List<String> strings = new ArrayList<>();
		for (Method method : cc.getDeclaredMethods()) {
			if (Modifier.isPrivate(method.getModifiers())
			// || Modifier.isStatic(method.getModifiers())
			)
				continue;
			strings.add(getMethodString(method));
		}
		
		for (Constructor<?> method : cc.getDeclaredConstructors()) {
			if (Modifier.isPrivate(method.getModifiers()))
				continue;
			strings.add(getConstructorString(method));
		}
		
		strings.add(toString(cc.getAnnotations()));
		for (Class<?> iClass : cc.getInterfaces()) {
			strings.add(iClass.getName());
		}
		if (cc.getSuperclass() != null && !cc.getSuperclass().getName().equals(Object.class.getName()))
			strings.add(cc.getSuperclass().getName());
		for (Field field : cc.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()))
				continue;
			strings.add(field.getType().getName() + " " + field.getName() + toString(field.getAnnotations()) + ";");
		}
		Collections.sort(strings);
		StringBuilder strBuilder = new StringBuilder();
		for (String methodString : strings) {
			strBuilder.append(methodString);
		}
		return strBuilder.toString();
	}
	
	private static String toString(Annotation[] a) {
		if (a == null)
			return "null";
		
		int iMax = a.length - 1;
		if (iMax == -1)
			return "[]";
		a = sort(a);
		
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			Annotation object = a[i];
			String[] ignore = new String[] { "annotationType", "equals", "hashCode", "toString" };
			Method[] declaredMethods = object.getClass().getDeclaredMethods();
			List<String> values = new ArrayList<>();
			for (Method method : declaredMethods) {
				if (Arrays.binarySearch(ignore, method.getName()) < 0) {
					Object value = getValue(object, method.getName());
					values.add(method.getName() + "=" + value.getClass() + ":" + value);
				}
			}
			b.append(values);
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}
	
	private static String toString(Annotation[][] a) {
		if (a == null)
			return "null";
		
		int iMax = a.length - 1;
		if (iMax == -1)
			return "[]";
		a = sort(a);
		
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			Annotation[] object = a[i];
			b.append(toString(object));
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}
	
	private static <T> T[] sort(T[] a) {
		
		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ObjectToStringComparator.INSTANCE);
		return a;
	}
	
	private static <T> T[][] sort(T[][] a) {
		
		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ObjectToStringComparator.INSTANCE);
		for (Object[] objects : a) {
			Arrays.sort(objects, ObjectToStringComparator.INSTANCE);
		}
		return a;
	}
	
	private static String getConstructorString(Constructor<?> method) {
		return Modifier.toString(method.getModifiers()) + " " + method.getName()
				+ getParams(method.getParameterTypes()) + toString(method.getDeclaredAnnotations())
				+ toString(method.getParameterAnnotations()) + Arrays.toString(sort(method.getExceptionTypes())) + ";";
	}
	
	private static String getMethodString(Method method) {
		return Modifier.toString(method.getModifiers()) + " " + getName(method.getReturnType()) + " "
				+ method.getName() + getParams(method.getParameterTypes()) + toString(method.getDeclaredAnnotations())
				+ toString(method.getParameterAnnotations()) + Arrays.toString(sort(method.getExceptionTypes())) + ";";
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
	
	private static Object getValue(Annotation annotation, String attributeName) {
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName);
			makeAccessible(method);
			return method.invoke(annotation);
		} catch (Exception ex) {
			return null;
		}
	}
	
	public static void makeAccessible(Method method) {
		if ((!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
				&& !method.isAccessible()) {
			method.setAccessible(true);
		}
	}
}
