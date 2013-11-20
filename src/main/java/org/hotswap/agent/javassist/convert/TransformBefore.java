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

public class TransformBefore extends TransformCall {
    protected org.hotswap.agent.javassist.CtClass[] parameterTypes;
    protected int locals;
    protected int maxLocals;
    protected byte[] saveCode, loadCode;

    public TransformBefore(Transformer next,
                           org.hotswap.agent.javassist.CtMethod origMethod, org.hotswap.agent.javassist.CtMethod beforeMethod)
            throws org.hotswap.agent.javassist.NotFoundException {
        super(next, origMethod, beforeMethod);

        // override
        methodDescriptor = origMethod.getMethodInfo2().getDescriptor();

        parameterTypes = origMethod.getParameterTypes();
        locals = 0;
        maxLocals = 0;
        saveCode = loadCode = null;
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.CodeAttribute attr) {
        super.initialize(cp, attr);
        locals = 0;
        maxLocals = attr.getMaxLocals();
        saveCode = loadCode = null;
    }

    protected int match(int c, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iterator,
                        int typedesc, org.hotswap.agent.javassist.bytecode.ConstPool cp) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        if (newIndex == 0) {
            String desc = org.hotswap.agent.javassist.bytecode.Descriptor.ofParameters(parameterTypes) + 'V';
            desc = org.hotswap.agent.javassist.bytecode.Descriptor.insertParameter(classname, desc);
            int nt = cp.addNameAndTypeInfo(newMethodname, desc);
            int ci = cp.addClassInfo(newClassname);
            newIndex = cp.addMethodrefInfo(ci, nt);
            constPool = cp;
        }

        if (saveCode == null)
            makeCode(parameterTypes, cp);

        return match2(pos, iterator);
    }

    protected int match2(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iterator) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        iterator.move(pos);
        iterator.insert(saveCode);
        iterator.insert(loadCode);
        int p = iterator.insertGap(3);
        iterator.writeByte(INVOKESTATIC, p);
        iterator.write16bit(newIndex, p + 1);
        iterator.insert(loadCode);
        return iterator.next();
    }

    public int extraLocals() {
        return locals;
    }

    protected void makeCode(org.hotswap.agent.javassist.CtClass[] paramTypes, org.hotswap.agent.javassist.bytecode.ConstPool cp) {
        org.hotswap.agent.javassist.bytecode.Bytecode save = new org.hotswap.agent.javassist.bytecode.Bytecode(cp, 0, 0);
        org.hotswap.agent.javassist.bytecode.Bytecode load = new org.hotswap.agent.javassist.bytecode.Bytecode(cp, 0, 0);

        int var = maxLocals;
        int len = (paramTypes == null) ? 0 : paramTypes.length;
        load.addAload(var);
        makeCode2(save, load, 0, len, paramTypes, var + 1);
        save.addAstore(var);

        saveCode = save.get();
        loadCode = load.get();
    }

    private void makeCode2(org.hotswap.agent.javassist.bytecode.Bytecode save, org.hotswap.agent.javassist.bytecode.Bytecode load,
                           int i, int n, org.hotswap.agent.javassist.CtClass[] paramTypes, int var) {
        if (i < n) {
            int size = load.addLoad(var, paramTypes[i]);
            makeCode2(save, load, i + 1, n, paramTypes, var + size);
            save.addStore(var, paramTypes[i]);
        } else
            locals = var - maxLocals;
    }
}
