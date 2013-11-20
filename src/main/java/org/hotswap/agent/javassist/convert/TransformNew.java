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

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;

final public class TransformNew extends Transformer {
    private int nested;
    private String classname, trapClass, trapMethod;

    public TransformNew(Transformer next,
                        String classname, String trapClass, String trapMethod) {
        super(next);
        this.classname = classname;
        this.trapClass = trapClass;
        this.trapMethod = trapMethod;
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.CodeAttribute attr) {
        nested = 0;
    }

    /**
     * Replace a sequence of
     * NEW classname
     * DUP
     * ...
     * INVOKESPECIAL
     * with
     * NOP
     * NOP
     * ...
     * INVOKESTATIC trapMethod in trapClass
     */
    public int transform(CtClass clazz, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iterator,
                         org.hotswap.agent.javassist.bytecode.ConstPool cp) throws CannotCompileException {
        int index;
        int c = iterator.byteAt(pos);
        if (c == NEW) {
            index = iterator.u16bitAt(pos + 1);
            if (cp.getClassInfo(index).equals(classname)) {
                if (iterator.byteAt(pos + 3) != DUP)
                    throw new CannotCompileException(
                            "NEW followed by no DUP was found");

                iterator.writeByte(NOP, pos);
                iterator.writeByte(NOP, pos + 1);
                iterator.writeByte(NOP, pos + 2);
                iterator.writeByte(NOP, pos + 3);
                ++nested;

                org.hotswap.agent.javassist.bytecode.StackMapTable smt
                        = (org.hotswap.agent.javassist.bytecode.StackMapTable) iterator.get().getAttribute(org.hotswap.agent.javassist.bytecode.StackMapTable.tag);
                if (smt != null)
                    smt.removeNew(pos);

                org.hotswap.agent.javassist.bytecode.StackMap sm
                        = (org.hotswap.agent.javassist.bytecode.StackMap) iterator.get().getAttribute(org.hotswap.agent.javassist.bytecode.StackMap.tag);
                if (sm != null)
                    sm.removeNew(pos);
            }
        } else if (c == INVOKESPECIAL) {
            index = iterator.u16bitAt(pos + 1);
            int typedesc = cp.isConstructor(classname, index);
            if (typedesc != 0 && nested > 0) {
                int methodref = computeMethodref(typedesc, cp);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);
                --nested;
            }
        }

        return pos;
    }

    private int computeMethodref(int typedesc, org.hotswap.agent.javassist.bytecode.ConstPool cp) {
        int classIndex = cp.addClassInfo(trapClass);
        int mnameIndex = cp.addUtf8Info(trapMethod);
        typedesc = cp.addUtf8Info(
                org.hotswap.agent.javassist.bytecode.Descriptor.changeReturnType(classname,
                        cp.getUtf8Info(typedesc)));
        return cp.addMethodrefInfo(classIndex,
                cp.addNameAndTypeInfo(mnameIndex, typedesc));
    }
}
