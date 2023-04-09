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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;

/**
 * CtClassSignature. Creates signature for given ctClass
 *
 * @author Erki Ehtla, Vladimir Dvorak
 */
public class CtClassSignature extends ClassSignatureBase {

    private CtClass ctClass;

    /**
     * @param ctClass the class for signature is to be counted
     */
    public CtClassSignature(CtClass ctClass) {
        this.ctClass = ctClass;

    }

    @Override
    public String getValue() throws Exception {
        List<String> strings = new ArrayList<>();

        if (hasElement(ClassSignatureElement.METHOD)) {
            boolean usePrivateMethod = hasElement(ClassSignatureElement.METHOD_PRIVATE);
            boolean useStaticMethod = hasElement(ClassSignatureElement.METHOD_STATIC);
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                if (!usePrivateMethod && Modifier.isPrivate(method.getModifiers()))
                    continue;
                if (!useStaticMethod && Modifier.isStatic(method.getModifiers()))
                    continue;
                if (method.getName().startsWith(SWITCH_TABLE_METHOD_PREFIX))
                    continue;
                strings.add(getMethodString(method));
            }
        }

        if (hasElement(ClassSignatureElement.CONSTRUCTOR)) {
            boolean usePrivateConstructor = hasElement(ClassSignatureElement.CONSTRUCTOR_PRIVATE);
            for (CtConstructor method : ctClass.getDeclaredConstructors()) {
                if (!usePrivateConstructor && Modifier.isPrivate(method.getModifiers()))
                    continue;
                strings.add(getConstructorString(method));
            }
        }

        if (hasElement(ClassSignatureElement.CLASS_ANNOTATION)) {
            strings.add(annotationToString(ctClass.getAvailableAnnotations()));
        }

        if (hasElement(ClassSignatureElement.INTERFACES)) {
            for (CtClass iClass : ctClass.getInterfaces()) {
                strings.add(iClass.getName());
            }
        }

        if (hasElement(ClassSignatureElement.SUPER_CLASS)) {
            String superclassName = ctClass.getSuperclassName();
            if (superclassName != null && !superclassName.equals(Object.class.getName()))
                strings.add(superclassName);
        }

        if (hasElement(ClassSignatureElement.FIELD)) {
            boolean useStaticField = hasElement(ClassSignatureElement.FIELD_STATIC);
            boolean useFieldAnnotation = hasElement(ClassSignatureElement.FIELD_ANNOTATION);
            for (CtField field : ctClass.getDeclaredFields()) {
                if (!useStaticField && Modifier.isStatic(field.getModifiers()))
                    continue;
                if (field.getName().startsWith(SWITCH_TABLE_METHOD_PREFIX))
                    continue;
                String fieldSignature = field.getType().getName() + " " + field.getName();
                if (useFieldAnnotation) {
                    fieldSignature += annotationToString(field.getAvailableAnnotations());
                }

                strings.add(fieldSignature + ";");
            }
        }
        Collections.sort(strings);
        StringBuilder strBuilder = new StringBuilder();
        for (String methodString : strings) {
            strBuilder.append(methodString);
        }
        return strBuilder.toString();
    }

    private String getName(CtClass ctClass) {
        return ctClass.getName();
    }

    private String getConstructorString(CtConstructor method) throws ClassNotFoundException, NotFoundException {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(Modifier.toString(method.getModifiers()) + " ");
        strBuilder.append(method.getDeclaringClass().getName());
        strBuilder.append(getParams(method.getParameterTypes()));
        if (hasElement(ClassSignatureElement.METHOD_ANNOTATION))
            strBuilder.append(annotationToString(method.getAvailableAnnotations()));
        if (hasElement(ClassSignatureElement.METHOD_PARAM_ANNOTATION))
            strBuilder.append(annotationToString(method.getAvailableParameterAnnotations()));
        if (hasElement(ClassSignatureElement.METHOD_EXCEPTION))
            strBuilder.append(toStringException(method.getExceptionTypes()));
        strBuilder.append(";");
        return strBuilder.toString();
    }

    private String getMethodString(CtMethod method) throws NotFoundException, ClassNotFoundException {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(Modifier.toString(method.getModifiers()) + " ");
        strBuilder.append(getName(method.getReturnType()) + " " + method.getName());
        strBuilder.append(getParams(method.getParameterTypes()));
        if (hasElement(ClassSignatureElement.METHOD_ANNOTATION))
            strBuilder.append(annotationToString(method.getAvailableAnnotations()));
        if (hasElement(ClassSignatureElement.METHOD_PARAM_ANNOTATION))
            strBuilder.append(annotationToString(method.getAvailableParameterAnnotations()));
        if (hasElement(ClassSignatureElement.METHOD_EXCEPTION))
            strBuilder.append(toStringException(method.getExceptionTypes()));
        strBuilder.append(";");
        return strBuilder.toString();
    }

    private String getParams(CtClass[] ctClasses) {
        StringBuilder strBuilder = new StringBuilder("(");
        boolean first = true;
        for (CtClass ctClass : ctClasses) {
            if (!first)
                strBuilder.append(",");
            else
                first = false;
            strBuilder.append(getName(ctClass));
        }
        strBuilder.append(")");
        return strBuilder.toString();
    }

    private String toStringException(CtClass[] a) {
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

    private CtClass[] sort(CtClass[] a) {
        a = Arrays.copyOf(a, a.length);
        Arrays.sort(a, CtClassComparator.INSTANCE);
        return a;
    }

    private static class CtClassComparator implements Comparator<CtClass> {
        public static final CtClassComparator INSTANCE = new CtClassComparator();

        @Override
        public int compare(CtClass o1, CtClass o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }
}
