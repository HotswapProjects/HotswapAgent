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
package org.hotswap.agent.plugin.jvm;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Get signature for an anonymous class to compare two versions (before and after reload).
 *
 * @author Jiri Bubnik
 */
public class AnonymousClassInfo {
    // name of the anonymous class
    String className;

    // signatures
    String classSignature;
    String methodSignature;
    String fieldsSignature;
    String enclosingMethodSignature;


    public AnonymousClassInfo(Class c) {
        this.className = c.getName();

        StringBuilder classSignature = new StringBuilder(c.getSuperclass().getName());
        for (Class intef : c.getInterfaces()) {
            classSignature.append(";");
            classSignature.append(intef.getName());
        }
        this.classSignature = classSignature.toString();

        StringBuilder methodsSignature = new StringBuilder();
        for (Method m : c.getDeclaredMethods()) {
            getMethodSignature(methodsSignature, m);
        }
        this.methodSignature = methodsSignature.toString();

        StringBuilder fieldsSignature = new StringBuilder();
        for (Field f : c.getDeclaredFields()) {
            // replace declarig class for constant because of unit tests (class name change)
            fieldsSignature.append(f.getType().getName());
            fieldsSignature.append(" ");
            fieldsSignature.append(f.getName());
            fieldsSignature.append(";");
        }
        this.fieldsSignature = fieldsSignature.toString();

        StringBuilder enclosingMethodSignature = new StringBuilder();
        Method enclosingMethod = c.getEnclosingMethod();
        if (enclosingMethod != null) {
            getMethodSignature(enclosingMethodSignature, enclosingMethod);
        }
        this.enclosingMethodSignature = enclosingMethodSignature.toString();

    }

    public AnonymousClassInfo(String className) {
        this.className = className;
    }

    private void getMethodSignature(StringBuilder methodsSignature, Method m) {
        methodsSignature.append(m.getReturnType().getName());
        methodsSignature.append(" ");
        methodsSignature.append(m.getName());
        methodsSignature.append("(");
        for (Class paramType : m.getParameterTypes())
            methodsSignature.append(paramType.getName());
        methodsSignature.append(")");
        methodsSignature.append(";");
    }

    public AnonymousClassInfo(CtClass c) {
        try {
            this.className = c.getName();

            StringBuilder classSignature = new StringBuilder(c.getSuperclassName());
            for (CtClass intef : c.getInterfaces()) {
                classSignature.append(";");
                classSignature.append(intef.getName());
            }
            this.classSignature = classSignature.toString();

            StringBuilder methodsSignature = new StringBuilder();
            for (CtMethod m : c.getDeclaredMethods()) {
                getMethodSignature(methodsSignature, m);
            }
            this.methodSignature = methodsSignature.toString();

            StringBuilder fieldsSignature = new StringBuilder();
            for (CtField f : c.getDeclaredFields()) {
                fieldsSignature.append(f.getType().getName());
                fieldsSignature.append(" ");
                fieldsSignature.append(f.getName());
                fieldsSignature.append(";");
            }
            this.fieldsSignature = fieldsSignature.toString();

            StringBuilder enclosingMethodSignature = new StringBuilder();
            try {
                CtMethod enclosingMethod = c.getEnclosingMethod();
                if (enclosingMethod != null) {
                    getMethodSignature(enclosingMethodSignature, enclosingMethod);
                }
            } catch (Exception e) {
                // OK, enclosing method not defined or out of scope
            }
            this.enclosingMethodSignature = enclosingMethodSignature.toString();

        } catch (Throwable t) {
            throw new IllegalStateException("Error creating AnonymousClassInfo from " + c.getName(), t);
        }
    }

    private void getMethodSignature(StringBuilder methodsSignature, CtMethod m) throws NotFoundException {
        methodsSignature.append(m.getReturnType().getName());
        methodsSignature.append(" ");
        methodsSignature.append(m.getName());
        methodsSignature.append("(");
        for (CtClass paramType : m.getParameterTypes())
            methodsSignature.append(paramType.getName());
        methodsSignature.append(")");
        methodsSignature.append(";");
    }

    public String getClassName() {
        return className;
    }

    public String getClassSignature() {
        return classSignature;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getFieldsSignature() {
        return fieldsSignature;
    }

    public String getEnclosingMethodSignature() {
        return enclosingMethodSignature;
    }

    /**
     * Exact match including enclosing method.
     */
    public boolean matchExact(AnonymousClassInfo other) {
        return getClassSignature().equals(other.getClassSignature()) &&
                getMethodSignature().equals(other.getMethodSignature()) &&
                getFieldsSignature().equals(other.getFieldsSignature()) &&
                getEnclosingMethodSignature().equals(other.getEnclosingMethodSignature());
    }

    /**
     * Exact match of class, interfaces, declared methods and fields.
     * May be different enclosing method.
     */
    public boolean matchSignatures(AnonymousClassInfo other) {
        return getClassSignature().equals(other.getClassSignature()) &&
                getMethodSignature().equals(other.getMethodSignature()) &&
                getFieldsSignature().equals(other.getFieldsSignature());
    }

    /**
     * The least matching variant - same class signature can be still resolved by hotswap.
     */
    public boolean matchClassSignature(AnonymousClassInfo other) {
        return getClassSignature().equals(other.getClassSignature());
    }
}

