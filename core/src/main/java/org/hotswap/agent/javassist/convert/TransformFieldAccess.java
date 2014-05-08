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

final public class TransformFieldAccess extends Transformer {
    private String newClassname, newFieldname;
    private String fieldname;
    private org.hotswap.agent.javassist.CtClass fieldClass;
    private boolean isPrivate;

    /* cache */
    private int newIndex;
    private org.hotswap.agent.javassist.bytecode.ConstPool constPool;

    public TransformFieldAccess(Transformer next, org.hotswap.agent.javassist.CtField field,
                                String newClassname, String newFieldname) {
        super(next);
        this.fieldClass = field.getDeclaringClass();
        this.fieldname = field.getName();
        this.isPrivate = org.hotswap.agent.javassist.Modifier.isPrivate(field.getModifiers());
        this.newClassname = newClassname;
        this.newFieldname = newFieldname;
        this.constPool = null;
    }

    public void initialize(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.CodeAttribute attr) {
        if (constPool != cp)
            newIndex = 0;
    }

    /**
     * Modify GETFIELD, GETSTATIC, PUTFIELD, and PUTSTATIC so that
     * a different field is accessed.  The new field must be declared
     * in a superclass of the class in which the original field is
     * declared.
     */
    public int transform(org.hotswap.agent.javassist.CtClass clazz, int pos,
                         org.hotswap.agent.javassist.bytecode.CodeIterator iterator, org.hotswap.agent.javassist.bytecode.ConstPool cp) {
        int c = iterator.byteAt(pos);
        if (c == GETFIELD || c == GETSTATIC
                || c == PUTFIELD || c == PUTSTATIC) {
            int index = iterator.u16bitAt(pos + 1);
            String typedesc
                    = TransformReadField.isField(clazz.getClassPool(), cp,
                    fieldClass, fieldname, isPrivate, index);
            if (typedesc != null) {
                if (newIndex == 0) {
                    int nt = cp.addNameAndTypeInfo(newFieldname,
                            typedesc);
                    newIndex = cp.addFieldrefInfo(
                            cp.addClassInfo(newClassname), nt);
                    constPool = cp;
                }

                iterator.write16bit(newIndex, pos + 1);
            }
        }

        return pos;
    }
}
