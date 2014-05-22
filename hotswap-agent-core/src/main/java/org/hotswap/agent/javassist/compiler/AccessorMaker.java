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

package org.hotswap.agent.javassist.compiler;

import java.util.HashMap;

/**
 * AccessorMaker maintains accessors to private members of an enclosing
 * class.  It is necessary for compiling a method in an inner class.
 */
public class AccessorMaker {
    private org.hotswap.agent.javassist.CtClass clazz;
    private int uniqueNumber;
    private HashMap accessors;

    static final String lastParamType = "Inner";

    public AccessorMaker(org.hotswap.agent.javassist.CtClass c) {
        clazz = c;
        uniqueNumber = 1;
        accessors = new HashMap();
    }

    public String getConstructor(org.hotswap.agent.javassist.CtClass c, String desc, org.hotswap.agent.javassist.bytecode.MethodInfo orig)
            throws CompileError {
        String key = "<init>:" + desc;
        String consDesc = (String) accessors.get(key);
        if (consDesc != null)
            return consDesc;     // already exists.

        consDesc = org.hotswap.agent.javassist.bytecode.Descriptor.appendParameter(lastParamType, desc);
        org.hotswap.agent.javassist.bytecode.ClassFile cf = clazz.getClassFile();    // turn on the modified flag.
        try {
            org.hotswap.agent.javassist.bytecode.ConstPool cp = cf.getConstPool();
            org.hotswap.agent.javassist.ClassPool pool = clazz.getClassPool();
            org.hotswap.agent.javassist.bytecode.MethodInfo minfo
                    = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, consDesc);
            minfo.setAccessFlags(0);
            minfo.addAttribute(new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(cp));
            org.hotswap.agent.javassist.bytecode.ExceptionsAttribute ea = orig.getExceptionsAttribute();
            if (ea != null)
                minfo.addAttribute(ea.copy(cp, null));

            org.hotswap.agent.javassist.CtClass[] params = org.hotswap.agent.javassist.bytecode.Descriptor.getParameterTypes(desc, pool);
            org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp);
            code.addAload(0);
            int regno = 1;
            for (int i = 0; i < params.length; ++i)
                regno += code.addLoad(regno, params[i]);
            code.setMaxLocals(regno + 1);    // the last parameter is added.
            code.addInvokespecial(clazz, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, desc);

            code.addReturn(null);
            minfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod(minfo);
        } catch (org.hotswap.agent.javassist.CannotCompileException e) {
            throw new CompileError(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new CompileError(e);
        }

        accessors.put(key, consDesc);
        return consDesc;
    }

    /**
     * Returns the name of the method for accessing a private method.
     *
     * @param name    the name of the private method.
     * @param desc    the descriptor of the private method.
     * @param accDesc the descriptor of the accessor method.  The first
     *                parameter type is <code>clazz</code>.
     *                If the private method is static,
     *                <code>accDesc<code> must be identical to <code>desc</code>.
     * @param orig    the method info of the private method.
     * @return
     */
    public String getMethodAccessor(String name, String desc, String accDesc,
                                    org.hotswap.agent.javassist.bytecode.MethodInfo orig)
            throws CompileError {
        String key = name + ":" + desc;
        String accName = (String) accessors.get(key);
        if (accName != null)
            return accName;     // already exists.

        org.hotswap.agent.javassist.bytecode.ClassFile cf = clazz.getClassFile();    // turn on the modified flag.
        accName = findAccessorName(cf);
        try {
            org.hotswap.agent.javassist.bytecode.ConstPool cp = cf.getConstPool();
            org.hotswap.agent.javassist.ClassPool pool = clazz.getClassPool();
            org.hotswap.agent.javassist.bytecode.MethodInfo minfo
                    = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, accName, accDesc);
            minfo.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC);
            minfo.addAttribute(new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(cp));
            org.hotswap.agent.javassist.bytecode.ExceptionsAttribute ea = orig.getExceptionsAttribute();
            if (ea != null)
                minfo.addAttribute(ea.copy(cp, null));

            org.hotswap.agent.javassist.CtClass[] params = org.hotswap.agent.javassist.bytecode.Descriptor.getParameterTypes(accDesc, pool);
            int regno = 0;
            org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp);
            for (int i = 0; i < params.length; ++i)
                regno += code.addLoad(regno, params[i]);

            code.setMaxLocals(regno);
            if (desc == accDesc)
                code.addInvokestatic(clazz, name, desc);
            else
                code.addInvokevirtual(clazz, name, desc);

            code.addReturn(org.hotswap.agent.javassist.bytecode.Descriptor.getReturnType(desc, pool));
            minfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod(minfo);
        } catch (org.hotswap.agent.javassist.CannotCompileException e) {
            throw new CompileError(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new CompileError(e);
        }

        accessors.put(key, accName);
        return accName;
    }

    /**
     * Returns the method_info representing the added getter.
     */
    public org.hotswap.agent.javassist.bytecode.MethodInfo getFieldGetter(org.hotswap.agent.javassist.bytecode.FieldInfo finfo, boolean is_static)
            throws CompileError {
        String fieldName = finfo.getName();
        String key = fieldName + ":getter";
        Object res = accessors.get(key);
        if (res != null)
            return (org.hotswap.agent.javassist.bytecode.MethodInfo) res;     // already exists.

        org.hotswap.agent.javassist.bytecode.ClassFile cf = clazz.getClassFile();    // turn on the modified flag.
        String accName = findAccessorName(cf);
        try {
            org.hotswap.agent.javassist.bytecode.ConstPool cp = cf.getConstPool();
            org.hotswap.agent.javassist.ClassPool pool = clazz.getClassPool();
            String fieldType = finfo.getDescriptor();
            String accDesc;
            if (is_static)
                accDesc = "()" + fieldType;
            else
                accDesc = "(" + org.hotswap.agent.javassist.bytecode.Descriptor.of(clazz) + ")" + fieldType;

            org.hotswap.agent.javassist.bytecode.MethodInfo minfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, accName, accDesc);
            minfo.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC);
            minfo.addAttribute(new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(cp));
            org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp);
            if (is_static) {
                code.addGetstatic(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            } else {
                code.addAload(0);
                code.addGetfield(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
                code.setMaxLocals(1);
            }

            code.addReturn(org.hotswap.agent.javassist.bytecode.Descriptor.toCtClass(fieldType, pool));
            minfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod(minfo);
            accessors.put(key, minfo);
            return minfo;
        } catch (org.hotswap.agent.javassist.CannotCompileException e) {
            throw new CompileError(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new CompileError(e);
        }
    }

    /**
     * Returns the method_info representing the added setter.
     */
    public org.hotswap.agent.javassist.bytecode.MethodInfo getFieldSetter(org.hotswap.agent.javassist.bytecode.FieldInfo finfo, boolean is_static)
            throws CompileError {
        String fieldName = finfo.getName();
        String key = fieldName + ":setter";
        Object res = accessors.get(key);
        if (res != null)
            return (org.hotswap.agent.javassist.bytecode.MethodInfo) res;     // already exists.

        org.hotswap.agent.javassist.bytecode.ClassFile cf = clazz.getClassFile();    // turn on the modified flag.
        String accName = findAccessorName(cf);
        try {
            org.hotswap.agent.javassist.bytecode.ConstPool cp = cf.getConstPool();
            org.hotswap.agent.javassist.ClassPool pool = clazz.getClassPool();
            String fieldType = finfo.getDescriptor();
            String accDesc;
            if (is_static)
                accDesc = "(" + fieldType + ")V";
            else
                accDesc = "(" + org.hotswap.agent.javassist.bytecode.Descriptor.of(clazz) + fieldType + ")V";

            org.hotswap.agent.javassist.bytecode.MethodInfo minfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, accName, accDesc);
            minfo.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC);
            minfo.addAttribute(new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(cp));
            org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp);
            int reg;
            if (is_static) {
                reg = code.addLoad(0, org.hotswap.agent.javassist.bytecode.Descriptor.toCtClass(fieldType, pool));
                code.addPutstatic(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            } else {
                code.addAload(0);
                reg = code.addLoad(1, org.hotswap.agent.javassist.bytecode.Descriptor.toCtClass(fieldType, pool))
                        + 1;
                code.addPutfield(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            }

            code.addReturn(null);
            code.setMaxLocals(reg);
            minfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod(minfo);
            accessors.put(key, minfo);
            return minfo;
        } catch (org.hotswap.agent.javassist.CannotCompileException e) {
            throw new CompileError(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new CompileError(e);
        }
    }

    private String findAccessorName(org.hotswap.agent.javassist.bytecode.ClassFile cf) {
        String accName;
        do {
            accName = "access$" + uniqueNumber++;
        } while (cf.getMethod(accName) != null);
        return accName;
    }
}
