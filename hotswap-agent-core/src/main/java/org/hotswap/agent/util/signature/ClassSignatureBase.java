/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.util.signature;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * ClassSignatureBase. Base class for class signature evaluation
 *
 * @author Erki Ehtla, Vladimir Dvorak
 */
public abstract class ClassSignatureBase {

    private static final String[] IGNORED_METHODS = new String[] { "annotationType", "equals", "hashCode", "toString" };

    private final Set<ClassSignatureElement> elements = new HashSet<>();

    protected static final String SWITCH_TABLE_METHOD_PREFIX = "$SWITCH_TABLE$"; // java stores switch table to class field, signature should ingore it

    /**
     * Evaluate and return signature value
     *
     * @return the signature value
     * @throws Exception
     */
    public abstract String getValue() throws Exception;

    /**
     * Adds the signature elements to set of used signature elements
     *
     * @param elems
     */
    public void addSignatureElements(ClassSignatureElement elems[]) {
        for (ClassSignatureElement element : elems) {
            elements.add(element);
        }
    }

    /**
     * Check if given signature element is set.
     *
     * @param element
     * @return true, if has given element
     */
    public boolean hasElement(ClassSignatureElement element) {
        return elements.contains(element);
    }

    protected String annotationToString(Object[] a) {
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";
        a = sort(a);
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;i < a.length; i++) {
            Annotation object = (Annotation) a[i];
            Method[] declaredMethods = object.getClass().getDeclaredMethods();
            b.append("(");
            boolean printComma = false;
            for (Method method : declaredMethods) {
                if (Arrays.binarySearch(IGNORED_METHODS, method.getName()) < 0) {
                    Object value = getAnnotationValue(object, method.getName());
                    if (value != null) {
                        if (printComma) {
                            b.append(",");
                        } else {
                            printComma = true;
                        }

                        if (value.getClass().isArray()) {
                            value = arrayToString(value);
                        }

                        b.append(method.getName() + "=" + value.getClass() + ":" + value);
                    }
                }
            }
            b.append(")");
            // TODO : sometimes for CtFile object.annotationType() is not known an it fails here
            // v.d. : uncommented in v1.1 alpha with javassist update (3.21) to check if there is still problem
            b.append(object.annotationType().getName());
            if (i<a.length-1) {
                b.append(",");
            }
        }
        b.append(']');
        return b.toString();
    }

    private Object arrayToString(Object value) {
        Object result = value;
        try {
            try {
                Method toStringMethod = Arrays.class.getMethod("toString", value.getClass());
                // maybe because value is a subclass of Object[]
                result = toStringMethod.invoke(null, value);
            } catch (NoSuchMethodException e) {
                if (value instanceof Object[]) {
                    Method toStringMethod = Arrays.class.getMethod("toString", Object[].class);
                    result = toStringMethod.invoke(null, value);
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e ) {
        }
        return result;
    }

    protected String annotationToString(Object[][] a) {
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
            b.append(annotationToString(object));
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    private <T> T[] sort(T[] a) {
        a = Arrays.copyOf(a, a.length);
        Arrays.sort(a, ToStringComparator.INSTANCE);
        return a;
    }

    private <T> T[][] sort(T[][] a) {
        a = Arrays.copyOf(a, a.length);
        Arrays.sort(a, ToStringComparator.INSTANCE);
        for (Object[] objects : a) {
            Arrays.sort(objects, ToStringComparator.INSTANCE);
        }
        return a;
    }

    private Object getAnnotationValue(Annotation annotation, String attributeName) {
        Method method = null;
        boolean acessibleSet = false;
        try {
            method = annotation.annotationType().getDeclaredMethod(attributeName);
            acessibleSet = makeAccessible(method);
            return method.invoke(annotation);
        } catch (Exception ex) {
            return null;
        } finally {
            if (method != null && acessibleSet) {
                method.setAccessible(false);
            }
        }
    }

    private boolean makeAccessible(Method method) {
        if ((!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
                && !method.isAccessible()) {
            method.setAccessible(true);
            return true;
        }
        return false;
    }

    protected static class ToStringComparator implements Comparator<Object> {
        public static final ToStringComparator INSTANCE = new ToStringComparator();
        @Override
        public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
        }
    }
}
