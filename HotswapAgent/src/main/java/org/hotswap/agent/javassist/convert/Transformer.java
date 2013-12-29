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

/**
 * Transformer and its subclasses are used for executing
 * code transformation specified by CodeConverter.
 *
 * @see org.hotswap.agent.javassist.CodeConverter
 */
public abstract class Transformer implements org.hotswap.agent.javassist.bytecode.Opcode {
    private Transformer next;

    public Transformer(Transformer t) {
        next = t;
    }

    public Transformer getNext() {
        return next;
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.CodeAttribute attr) {
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.CtClass clazz, org.hotswap.agent.javassist.bytecode.MethodInfo minfo) throws org.hotswap.agent.javassist.CannotCompileException {
        initialize(cp, minfo.getCodeAttribute());
    }

    public void clean() {
    }

    public abstract int transform(org.hotswap.agent.javassist.CtClass clazz, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator it,
                                  org.hotswap.agent.javassist.bytecode.ConstPool cp) throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.bytecode.BadBytecode;

    public int extraLocals() {
        return 0;
    }

    public int extraStack() {
        return 0;
    }
}
