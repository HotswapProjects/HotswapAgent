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

import org.hotswap.agent.javassist.compiler.JvstCodeGen;

import java.util.Hashtable;

class CtNewWrappedMethod {

    private static final String addedWrappedMethod = "_added_m$";

    public static CtMethod wrapped(CtClass returnType, String mname,
                                   CtClass[] parameterTypes,
                                   CtClass[] exceptionTypes,
                                   CtMethod body, CtMethod.ConstParameter constParam,
                                   CtClass declaring)
            throws CannotCompileException {
        CtMethod mt = new CtMethod(returnType, mname, parameterTypes,
                declaring);
        mt.setModifiers(body.getModifiers());
        try {
            mt.setExceptionTypes(exceptionTypes);
        } catch (NotFoundException e) {
            throw new CannotCompileException(e);
        }

        org.hotswap.agent.javassist.bytecode.Bytecode code = makeBody(declaring, declaring.getClassFile2(), body,
                parameterTypes, returnType, constParam);
        org.hotswap.agent.javassist.bytecode.MethodInfo minfo = mt.getMethodInfo2();
        minfo.setCodeAttribute(code.toCodeAttribute());
        // a stack map has been already created. 
        return mt;
    }

    static org.hotswap.agent.javassist.bytecode.Bytecode makeBody(CtClass clazz, org.hotswap.agent.javassist.bytecode.ClassFile classfile,
                                                                  CtMethod wrappedBody,
                                                                  CtClass[] parameters,
                                                                  CtClass returnType,
                                                                  CtMethod.ConstParameter cparam)
            throws CannotCompileException {
        boolean isStatic = Modifier.isStatic(wrappedBody.getModifiers());
        org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(classfile.getConstPool(), 0, 0);
        int stacksize = makeBody0(clazz, classfile, wrappedBody, isStatic,
                parameters, returnType, cparam, code);
        code.setMaxStack(stacksize);
        code.setMaxLocals(isStatic, parameters, 0);
        return code;
    }

    /* The generated method body does not need a stack map table
     * because it does not contain a branch instruction.
     */
    protected static int makeBody0(CtClass clazz, org.hotswap.agent.javassist.bytecode.ClassFile classfile,
                                   CtMethod wrappedBody,
                                   boolean isStatic, CtClass[] parameters,
                                   CtClass returnType, CtMethod.ConstParameter cparam,
                                   org.hotswap.agent.javassist.bytecode.Bytecode code)
            throws CannotCompileException {
        if (!(clazz instanceof CtClassType))
            throw new CannotCompileException("bad declaring class"
                    + clazz.getName());

        if (!isStatic)
            code.addAload(0);

        int stacksize = compileParameterList(code, parameters,
                (isStatic ? 0 : 1));
        int stacksize2;
        String desc;
        if (cparam == null) {
            stacksize2 = 0;
            desc = CtMethod.ConstParameter.defaultDescriptor();
        } else {
            stacksize2 = cparam.compile(code);
            desc = cparam.descriptor();
        }

        checkSignature(wrappedBody, desc);

        String bodyname;
        try {
            bodyname = addBodyMethod((CtClassType) clazz, classfile,
                    wrappedBody);
            /* if an exception is thrown below, the method added above
             * should be removed. (future work :<)
             */
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new CannotCompileException(e);
        }

        if (isStatic)
            code.addInvokestatic(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, bodyname, desc);
        else
            code.addInvokespecial(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, bodyname, desc);

        compileReturn(code, returnType);        // consumes 2 stack entries

        if (stacksize < stacksize2 + 2)
            stacksize = stacksize2 + 2;

        return stacksize;
    }

    private static void checkSignature(CtMethod wrappedBody,
                                       String descriptor)
            throws CannotCompileException {
        if (!descriptor.equals(wrappedBody.getMethodInfo2().getDescriptor()))
            throw new CannotCompileException(
                    "wrapped method with a bad signature: "
                            + wrappedBody.getDeclaringClass().getName()
                            + '.' + wrappedBody.getName());
    }

    private static String addBodyMethod(CtClassType clazz,
                                        org.hotswap.agent.javassist.bytecode.ClassFile classfile,
                                        CtMethod src)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode, CannotCompileException {
        Hashtable bodies = clazz.getHiddenMethods();
        String bodyname = (String) bodies.get(src);
        if (bodyname == null) {
            do {
                bodyname = addedWrappedMethod + clazz.getUniqueNumber();
            } while (classfile.getMethod(bodyname) != null);
            ClassMap map = new ClassMap();
            map.put(src.getDeclaringClass().getName(), clazz.getName());
            org.hotswap.agent.javassist.bytecode.MethodInfo body = new org.hotswap.agent.javassist.bytecode.MethodInfo(classfile.getConstPool(),
                    bodyname, src.getMethodInfo2(),
                    map);
            int acc = body.getAccessFlags();
            body.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.setPrivate(acc));
            body.addAttribute(new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(classfile.getConstPool()));
            // a stack map is copied.  rebuilding it is not needed.
            classfile.addMethod(body);
            bodies.put(src, bodyname);
            org.hotswap.agent.javassist.CtMember.Cache cache = clazz.hasMemberCache();
            if (cache != null)
                cache.addMethod(new CtMethod(body, clazz));
        }

        return bodyname;
    }

    /* compileParameterList() returns the stack size used
     * by the produced code.
     *
     * @param regno     the index of the local variable in which
     *                  the first argument is received.
     *                  (0: static method, 1: regular method.)
     */
    static int compileParameterList(org.hotswap.agent.javassist.bytecode.Bytecode code,
                                    CtClass[] params, int regno) {
        return JvstCodeGen.compileParameterList(code, params, regno);
    }

    /*
     * The produced codes cosume 1 or 2 stack entries.
     */
    private static void compileReturn(org.hotswap.agent.javassist.bytecode.Bytecode code, CtClass type) {
        if (type.isPrimitive()) {
            CtPrimitiveType pt = (CtPrimitiveType) type;
            if (pt != CtClass.voidType) {
                String wrapper = pt.getWrapperName();
                code.addCheckcast(wrapper);
                code.addInvokevirtual(wrapper, pt.getGetMethodName(),
                        pt.getGetMethodDescriptor());
            }

            code.addOpcode(pt.getReturnOp());
        } else {
            code.addCheckcast(type);
            code.addOpcode(org.hotswap.agent.javassist.bytecode.Bytecode.ARETURN);
        }
    }
}
