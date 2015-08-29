package org.hotswap.agent.plugin.spring.signature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.NotFoundException;

/**
 * String representation of a CtClass instance. Consists of a super class name(if not Object), constructors and methods
 * (names, return types, parameter types, parameter annotations, exceptions), interface names, class and field
 * annotations ordered alphabetically.
 *
 * @author Erki Ehtla
 *
 */
public class CtClassSignature {

	private static String getConstructorString(CtConstructor method) throws ClassNotFoundException, NotFoundException {
		return Modifier.toString(method.getModifiers()) + " " + method.getDeclaringClass().getName()
				+ getParams(method.getParameterTypes()) + toStringA(method.getAnnotations())
				+ toStringA(method.getParameterAnnotations()) + toStringE(method.getExceptionTypes()) + ";";
	}

	private static String getMethodString(CtMethod method) throws ClassNotFoundException, NotFoundException {
		return Modifier.toString(method.getModifiers()) + " " + getName(method.getReturnType()) + " "
				+ method.getName() + getParams(method.getParameterTypes()) + toStringA(method.getAnnotations())
				+ toStringA(method.getParameterAnnotations()) + toStringE(method.getExceptionTypes()) + ";";
	}

	private static String getParams(CtClass[] ctClasses) {
		StringBuilder strB = new StringBuilder("(");
		boolean first = true;
		for (CtClass ctClass : ctClasses) {
			if (!first)
				strB.append(",");
			else
				first = false;
			strB.append(getName(ctClass));
		}
		strB.append(")");
		return strB.toString();
	}

	private static String getName(CtClass ctClass) {
		// if (ctClass.isArray())
		// return Descriptor.toString(ctClass.getName());
		// else
		return ctClass.getName();
	}

	public static String get(CtClass cc) throws NotFoundException, ClassNotFoundException {
		List<String> strings = new ArrayList<>();
		for (CtMethod method : cc.getDeclaredMethods()) {
			if (Modifier.isPrivate(method.getModifiers()))
				continue;
			strings.add(getMethodString(method));
		}

		for (CtConstructor method : cc.getDeclaredConstructors()) {
			if (Modifier.isPrivate(method.getModifiers()))
				continue;
			strings.add(getConstructorString(method));
		}

		strings.add(toStringA(cc.getAnnotations()));
		for (CtClass iClass : cc.getInterfaces()) {
			strings.add(iClass.getName());
		}
		if (cc.getSuperclass() != null && !cc.getSuperclass().getName().equals(Object.class.getName()))
			strings.add(cc.getSuperclass().getName());
		for (CtField field : cc.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()))
				continue;
			strings.add(field.getType().getName() + " " + field.getName() + toStringA(field.getAnnotations()) + ";");
		}
		Collections.sort(strings);
		StringBuilder strBuilder = new StringBuilder();
		for (String methodString : strings) {
			strBuilder.append(methodString);
		}
		return strBuilder.toString();
	}

	private static String toStringE(CtClass[] a) {
		if (a == null)
			return "null";
		int iMax = a.length - 1;
		if (iMax == -1)
			return "[]";
		a = sort(a);

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			b.append("class " + a[i].getName());
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	private static String toStringA(Object[] a) {
		if (a == null)
			return "null";
		int iMax = a.length - 1;
		if (iMax == -1)
			return "[]";
		a = sort(a);
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			Annotation object = (Annotation) a[i];
			String[] ignore = new String[] { "annotationType", "equals", "hashCode", "toString" };
			Method[] declaredMethods = object.getClass().getDeclaredMethods();
			List<String> values = new ArrayList<>();
			for (Method method : declaredMethods) {
				if (Arrays.binarySearch(ignore, method.getName()) < 0) {
					Object value = getValue(object, method.getName());
					if (value != null)
						values.add(method.getName() + "=" + value.getClass() + ":" + value);
				}
			}
			b.append(values);
			// b.append(object.toString() + "()");
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	private static String toStringA(Object[][] a) {
		if (a == null)
			return "null";

		int iMax = a.length - 1;
		if (iMax == -1)
			return "[]";
		a = sort(a);

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			Object[] object = a[i];
			b.append(toStringA(object));
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	private static <T> T[] sort(T[] a) {

		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ToStringComparator.INSTANCE);
		return a;
	}

	private static CtClass[] sort(CtClass[] a) {

		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, CtClassComparator.INSTANCE);
		return a;
	}

	private static <T> T[][] sort(T[][] a) {

		a = Arrays.copyOf(a, a.length);
		Arrays.sort(a, ToStringComparator.INSTANCE);
		for (Object[] objects : a) {
			Arrays.sort(objects, ToStringComparator.INSTANCE);
		}
		return a;
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
