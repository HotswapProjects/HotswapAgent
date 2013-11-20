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

package org.hotswap.agent.javassist.convert;

import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.NotFoundException;

public class TransformCall extends Transformer {
    protected String classname, methodname, methodDescriptor;
    protected String newClassname, newMethodname;
    protected boolean newMethodIsPrivate;

    /* cache */
    protected int newIndex;
    protected org.hotswap.agent.javassist.bytecode.ConstPool constPool;

    public TransformCall(Transformer next, CtMethod origMethod,
                         CtMethod substMethod) {
        this(next, origMethod.getName(), substMethod);
        classname = origMethod.getDeclaringClass().getName();
    }

    public TransformCall(Transformer next, String oldMethodName,
                         CtMethod substMethod) {
        super(next);
        methodname = oldMethodName;
        methodDescriptor = substMethod.getMethodInfo2().getDescriptor();
        classname = newClassname = substMethod.getDeclaringClass().getName();
        newMethodname = substMethod.getName();
        constPool = null;
        newMethodIsPrivate = Modifier.isPrivate(substMethod.getModifiers());
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.CodeAttribute attr) {
        if (constPool != cp)
            newIndex = 0;
    }

    /**
     * Modify INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC and INVOKEVIRTUAL
     * so that a different method is invoked.  The class name in the operand
     * of these instructions might be a subclass of the target class specified
     * by <code>classname</code>.   This method transforms the instruction
     * in that case unless the subclass overrides the target method.
     */
    public int transform(org.hotswap.agent.javassist.CtClass clazz, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iterator,
                         org.hotswap.agent.javassist.bytecode.ConstPool cp) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int c = iterator.byteAt(pos);
        if (c == INVOKEINTERFACE || c == INVOKESPECIAL
                || c == INVOKESTATIC || c == INVOKEVIRTUAL) {
            int index = iterator.u16bitAt(pos + 1);
            String cname = cp.eqMember(methodname, methodDescriptor, index);
            if (cname != null && matchClass(cname, clazz.getClassPool())) {
                int ntinfo = cp.getMemberNameAndType(index);
                pos = match(c, pos, iterator,
                        cp.getNameAndTypeDescriptor(ntinfo), cp);
            }
        }

        return pos;
    }

    private boolean matchClass(String name, org.hotswap.agent.javassist.ClassPool pool) {
        if (classname.equals(name))
            return true;

        try {
            org.hotswap.agent.javassist.CtClass clazz = pool.get(name);
            org.hotswap.agent.javassist.CtClass declClazz = pool.get(classname);
            if (clazz.subtypeOf(declClazz))
                try {
                    CtMethod m = clazz.getMethod(methodname, methodDescriptor);
                    return m.getDeclaringClass().getName().equals(classname);
                } catch (NotFoundException e) {
                    // maybe the original method has been removed.
                    return true;
                }
        } catch (NotFoundException e) {
            return false;
        }

        return false;
    }

    protected int match(int c, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iterator,
                        int typedesc, org.hotswap.agent.javassist.bytecode.ConstPool cp) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        if (newIndex == 0) {
            int nt = cp.addNameAndTypeInfo(cp.addUtf8Info(newMethodname),
                    typedesc);
            int ci = cp.addClassInfo(newClassname);
            if (c == INVOKEINTERFACE)
                newIndex = cp.addInterfaceMethodrefInfo(ci, nt);
            else {
                if (newMethodIsPrivate && c == INVOKEVIRTUAL)
                    iterator.writeByte(INVOKESPECIAL, pos);

                newIndex = cp.addMethodrefInfo(ci, nt);
            }

            constPool = cp;
        }

        iterator.write16bit(newIndex, pos + 1);
        return pos;
    }
}
