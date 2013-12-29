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

package org.hotswap.agent.javassist;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Utility for calculating serialVersionUIDs for Serializable classes.
 *
 * @author Bob Lee (crazybob@crazybob.org)
 * @author modified by Shigeru Chiba
 */
public class SerialVersionUID {

    /**
     * Adds serialVersionUID if one does not already exist. Call this before
     * modifying a class to maintain serialization compatability.
     */
    public static void setSerialVersionUID(org.hotswap.agent.javassist.CtClass clazz)
            throws org.hotswap.agent.javassist.CannotCompileException, NotFoundException {
        // check for pre-existing field.
        try {
            clazz.getDeclaredField("serialVersionUID");
            return;
        } catch (NotFoundException e) {
        }

        // check if the class is serializable.
        if (!isSerializable(clazz))
            return;

        // add field with default value.
        org.hotswap.agent.javassist.CtField field = new org.hotswap.agent.javassist.CtField(org.hotswap.agent.javassist.CtClass.longType, "serialVersionUID",
                clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.STATIC |
                Modifier.FINAL);
        clazz.addField(field, calculateDefault(clazz) + "L");
    }

    /**
     * Does the class implement Serializable?
     */
    private static boolean isSerializable(org.hotswap.agent.javassist.CtClass clazz)
            throws NotFoundException {
        ClassPool pool = clazz.getClassPool();
        return clazz.subtypeOf(pool.get("java.io.Serializable"));
    }

    /**
     * Calculate default value. See Java Serialization Specification, Stream
     * Unique Identifiers.
     */
    static long calculateDefault(org.hotswap.agent.javassist.CtClass clazz)
            throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            org.hotswap.agent.javassist.bytecode.ClassFile classFile = clazz.getClassFile();

            // class name.
            String javaName = javaName(clazz);
            out.writeUTF(javaName);

            org.hotswap.agent.javassist.CtMethod[] methods = clazz.getDeclaredMethods();

            // class modifiers.
            int classMods = clazz.getModifiers();
            if ((classMods & Modifier.INTERFACE) != 0)
                if (methods.length > 0)
                    classMods = classMods | Modifier.ABSTRACT;
                else
                    classMods = classMods & ~Modifier.ABSTRACT;

            out.writeInt(classMods);

            // interfaces.
            String[] interfaces = classFile.getInterfaces();
            for (int i = 0; i < interfaces.length; i++)
                interfaces[i] = javaName(interfaces[i]);

            Arrays.sort(interfaces);
            for (int i = 0; i < interfaces.length; i++)
                out.writeUTF(interfaces[i]);

            // fields.
            org.hotswap.agent.javassist.CtField[] fields = clazz.getDeclaredFields();
            Arrays.sort(fields, new Comparator() {
                public int compare(Object o1, Object o2) {
                    org.hotswap.agent.javassist.CtField field1 = (org.hotswap.agent.javassist.CtField) o1;
                    org.hotswap.agent.javassist.CtField field2 = (org.hotswap.agent.javassist.CtField) o2;
                    return field1.getName().compareTo(field2.getName());
                }
            });

            for (int i = 0; i < fields.length; i++) {
                org.hotswap.agent.javassist.CtField field = (org.hotswap.agent.javassist.CtField) fields[i];
                int mods = field.getModifiers();
                if (((mods & Modifier.PRIVATE) == 0) ||
                        ((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0)) {
                    out.writeUTF(field.getName());
                    out.writeInt(mods);
                    out.writeUTF(field.getFieldInfo2().getDescriptor());
                }
            }

            // static initializer.
            if (classFile.getStaticInitializer() != null) {
                out.writeUTF("<clinit>");
                out.writeInt(Modifier.STATIC);
                out.writeUTF("()V");
            }

            // constructors.
            CtConstructor[] constructors = clazz.getDeclaredConstructors();
            Arrays.sort(constructors, new Comparator() {
                public int compare(Object o1, Object o2) {
                    CtConstructor c1 = (CtConstructor) o1;
                    CtConstructor c2 = (CtConstructor) o2;
                    return c1.getMethodInfo2().getDescriptor().compareTo(
                            c2.getMethodInfo2().getDescriptor());
                }
            });

            for (int i = 0; i < constructors.length; i++) {
                CtConstructor constructor = constructors[i];
                int mods = constructor.getModifiers();
                if ((mods & Modifier.PRIVATE) == 0) {
                    out.writeUTF("<init>");
                    out.writeInt(mods);
                    out.writeUTF(constructor.getMethodInfo2()
                            .getDescriptor().replace('/', '.'));
                }
            }

            // methods.
            Arrays.sort(methods, new Comparator() {
                public int compare(Object o1, Object o2) {
                    org.hotswap.agent.javassist.CtMethod m1 = (org.hotswap.agent.javassist.CtMethod) o1;
                    org.hotswap.agent.javassist.CtMethod m2 = (org.hotswap.agent.javassist.CtMethod) o2;
                    int value = m1.getName().compareTo(m2.getName());
                    if (value == 0)
                        value = m1.getMethodInfo2().getDescriptor()
                                .compareTo(m2.getMethodInfo2().getDescriptor());

                    return value;
                }
            });

            for (int i = 0; i < methods.length; i++) {
                org.hotswap.agent.javassist.CtMethod method = methods[i];
                int mods = method.getModifiers()
                        & (Modifier.PUBLIC | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.STATIC
                        | Modifier.FINAL | Modifier.SYNCHRONIZED
                        | Modifier.NATIVE | Modifier.ABSTRACT | Modifier.STRICT);
                if ((mods & Modifier.PRIVATE) == 0) {
                    out.writeUTF(method.getName());
                    out.writeInt(mods);
                    out.writeUTF(method.getMethodInfo2()
                            .getDescriptor().replace('/', '.'));
                }
            }

            // calculate hash.
            out.flush();
            MessageDigest digest = MessageDigest.getInstance("SHA");
            byte[] digested = digest.digest(bout.toByteArray());
            long hash = 0;
            for (int i = Math.min(digested.length, 8) - 1; i >= 0; i--)
                hash = (hash << 8) | (digested[i] & 0xFF);

            return hash;
        } catch (IOException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    private static String javaName(org.hotswap.agent.javassist.CtClass clazz) {
        return org.hotswap.agent.javassist.bytecode.Descriptor.toJavaName(org.hotswap.agent.javassist.bytecode.Descriptor.toJvmName(clazz));
    }

    private static String javaName(String name) {
        return org.hotswap.agent.javassist.bytecode.Descriptor.toJavaName(org.hotswap.agent.javassist.bytecode.Descriptor.toJvmName(name));
    }
}
