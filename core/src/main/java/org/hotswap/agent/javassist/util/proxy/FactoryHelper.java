/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package org.hotswap.agent.javassist.util.proxy;

import java.io.*;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

/**
 * A helper class for implementing <code>ProxyFactory</code>.
 * The users of <code>ProxyFactory</code> do not have to see this class.
 *
 * @see ProxyFactory
 */
public class FactoryHelper {
    private static java.lang.reflect.Method defineClass1, defineClass2;

    static {
        try {
            Class cl = Class.forName("java.lang.ClassLoader");
            defineClass1 = SecurityActions.getDeclaredMethod(
                    cl,
                    "defineClass",
                    new Class[]{String.class, byte[].class,
                            int.class, int.class});

            defineClass2 = SecurityActions.getDeclaredMethod(
                    cl,
                    "defineClass",
                    new Class[]{String.class, byte[].class,
                            int.class, int.class, ProtectionDomain.class});
        } catch (Exception e) {
            throw new RuntimeException("cannot initialize");
        }
    }

    /**
     * Returns an index for accessing arrays in this class.
     *
     * @throws RuntimeException if a given type is not a primitive type.
     */
    public static final int typeIndex(Class type) {
        Class[] list = primitiveTypes;
        int n = list.length;
        for (int i = 0; i < n; i++)
            if (list[i] == type)
                return i;

        throw new RuntimeException("bad type:" + type.getName());
    }

    /**
     * <code>Class</code> objects representing primitive types.
     */
    public static final Class[] primitiveTypes = {
            Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE,
            Long.TYPE, Float.TYPE, Double.TYPE, Void.TYPE
    };

    /**
     * The fully-qualified names of wrapper classes for primitive types.
     */
    public static final String[] wrapperTypes = {
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Void"
    };

    /**
     * The descriptors of the constructors of wrapper classes.
     */
    public static final String[] wrapperDesc = {
            "(Z)V", "(B)V", "(C)V", "(S)V", "(I)V", "(J)V",
            "(F)V", "(D)V"
    };

    /**
     * The names of methods for obtaining a primitive value
     * from a wrapper object.  For example, <code>intValue()</code>
     * is such a method for obtaining an integer value from a
     * <code>java.lang.Integer</code> object.
     */
    public static final String[] unwarpMethods = {
            "booleanValue", "byteValue", "charValue", "shortValue",
            "intValue", "longValue", "floatValue", "doubleValue"
    };

    /**
     * The descriptors of the unwrapping methods contained
     * in <code>unwrapMethods</code>.
     */
    public static final String[] unwrapDesc = {
            "()Z", "()B", "()C", "()S", "()I", "()J", "()F", "()D"
    };

    /**
     * The data size of primitive types.  <code>long</code>
     * and <code>double</code> are 2; the others are 1.
     */
    public static final int[] dataSize = {
            1, 1, 1, 1, 1, 2, 1, 2
    };

    /**
     * Loads a class file by a given class loader.
     * This method uses a default protection domain for the class
     * but it may not work with a security manager or a sigend jar file.
     *
     * @see #toClass(org.hotswap.agent.javassist.bytecode.ClassFile, ClassLoader, ProtectionDomain)
     */
    public static Class toClass(org.hotswap.agent.javassist.bytecode.ClassFile cf, ClassLoader loader)
            throws org.hotswap.agent.javassist.CannotCompileException {
        return toClass(cf, loader, null);
    }

    /**
     * Loads a class file by a given class loader.
     *
     * @param domain if it is null, a default domain is used.
     * @since 3.3
     */
    public static Class toClass(org.hotswap.agent.javassist.bytecode.ClassFile cf, ClassLoader loader, ProtectionDomain domain)
            throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            byte[] b = toBytecode(cf);
            Method method;
            Object[] args;
            if (domain == null) {
                method = defineClass1;
                args = new Object[]{cf.getName(), b, new Integer(0),
                        new Integer(b.length)};
            } else {
                method = defineClass2;
                args = new Object[]{cf.getName(), b, new Integer(0),
                        new Integer(b.length), domain};
            }

            return toClass2(method, loader, args);
        } catch (RuntimeException e) {
            throw e;
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e.getTargetException());
        } catch (Exception e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    private static synchronized Class toClass2(Method method,
                                               ClassLoader loader, Object[] args)
            throws Exception {
        SecurityActions.setAccessible(method, true);
        Class clazz = (Class) method.invoke(loader, args);
        SecurityActions.setAccessible(method, false);
        return clazz;
    }

    private static byte[] toBytecode(org.hotswap.agent.javassist.bytecode.ClassFile cf) throws IOException {
        ByteArrayOutputStream barray = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(barray);
        try {
            cf.write(out);
        } finally {
            out.close();
        }

        return barray.toByteArray();
    }

    /**
     * Writes a class file.
     */
    public static void writeFile(org.hotswap.agent.javassist.bytecode.ClassFile cf, String directoryName)
            throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            writeFile0(cf, directoryName);
        } catch (IOException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    private static void writeFile0(org.hotswap.agent.javassist.bytecode.ClassFile cf, String directoryName)
            throws org.hotswap.agent.javassist.CannotCompileException, IOException {
        String classname = cf.getName();
        String filename = directoryName + File.separatorChar
                + classname.replace('.', File.separatorChar) + ".class";
        int pos = filename.lastIndexOf(File.separatorChar);
        if (pos > 0) {
            String dir = filename.substring(0, pos);
            if (!dir.equals("."))
                new File(dir).mkdirs();
        }

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(filename)));
        try {
            cf.write(out);
        } catch (IOException e) {
            throw e;
        } finally {
            out.close();
        }
    }
}
