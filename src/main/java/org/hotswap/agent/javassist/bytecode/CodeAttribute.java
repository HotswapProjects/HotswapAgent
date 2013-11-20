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

package org.hotswap.agent.javassist.bytecode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>Code_attribute</code>.
 * <p/>
 * <p>To browse the <code>code</code> field of
 * a <code>Code_attribute</code> structure,
 * use <code>CodeIterator</code>.
 *
 * @see org.hotswap.agent.javassist.bytecode.CodeIterator
 * @see #iterator()
 */
public class CodeAttribute extends org.hotswap.agent.javassist.bytecode.AttributeInfo implements Opcode {
    /**
     * The name of this attribute <code>"Code"</code>.
     */
    public static final String tag = "Code";

    // code[] is stored in AttributeInfo.info.

    private int maxStack;
    private int maxLocals;
    private org.hotswap.agent.javassist.bytecode.ExceptionTable exceptions;
    private ArrayList attributes;

    /**
     * Constructs a <code>Code_attribute</code>.
     *
     * @param cp     constant pool table
     * @param stack  <code>max_stack</code>
     * @param locals <code>max_locals</code>
     * @param code   <code>code[]</code>
     * @param etable <code>exception_table[]</code>
     */
    public CodeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp, int stack, int locals, byte[] code,
                         org.hotswap.agent.javassist.bytecode.ExceptionTable etable) {
        super(cp, tag);
        maxStack = stack;
        maxLocals = locals;
        info = code;
        exceptions = etable;
        attributes = new ArrayList();
    }

    /**
     * Constructs a copy of <code>Code_attribute</code>.
     * Specified class names are replaced during the copy.
     *
     * @param cp         constant pool table.
     * @param src        source Code attribute.
     * @param classnames pairs of replaced and substituted
     *                   class names.
     */
    private CodeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp, CodeAttribute src, Map classnames)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        super(cp, tag);

        maxStack = src.getMaxStack();
        maxLocals = src.getMaxLocals();
        exceptions = src.getExceptionTable().copy(cp, classnames);
        attributes = new ArrayList();
        List src_attr = src.getAttributes();
        int num = src_attr.size();
        for (int i = 0; i < num; ++i) {
            org.hotswap.agent.javassist.bytecode.AttributeInfo ai = (org.hotswap.agent.javassist.bytecode.AttributeInfo) src_attr.get(i);
            attributes.add(ai.copy(cp, classnames));
        }

        info = src.copyCode(cp, classnames, exceptions, this);
    }

    CodeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp, int name_id, DataInputStream in)
            throws IOException {
        super(cp, name_id, (byte[]) null);
        int attr_len = in.readInt();

        maxStack = in.readUnsignedShort();
        maxLocals = in.readUnsignedShort();

        int code_len = in.readInt();
        info = new byte[code_len];
        in.readFully(info);

        exceptions = new org.hotswap.agent.javassist.bytecode.ExceptionTable(cp, in);

        attributes = new ArrayList();
        int num = in.readUnsignedShort();
        for (int i = 0; i < num; ++i)
            attributes.add(org.hotswap.agent.javassist.bytecode.AttributeInfo.read(cp, in));
    }

    /**
     * Makes a copy.  Class names are replaced according to the
     * given <code>Map</code> object.
     *
     * @param newCp      the constant pool table used by the new copy.
     * @param classnames pairs of replaced and substituted
     *                   class names.
     * @return <code>CodeAttribute</code> object.
     * @throws RuntimeCopyException if a <code>BadBytecode</code>
     *                              exception is thrown, it is
     *                              converted into
     *                              <code>RuntimeCopyException</code>.
     */
    public org.hotswap.agent.javassist.bytecode.AttributeInfo copy(org.hotswap.agent.javassist.bytecode.ConstPool newCp, Map classnames)
            throws RuntimeCopyException {
        try {
            return new CodeAttribute(newCp, this, classnames);
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new RuntimeCopyException("bad bytecode. fatal?");
        }
    }

    /**
     * An exception that may be thrown by <code>copy()</code>
     * in <code>CodeAttribute</code>.
     */
    public static class RuntimeCopyException extends RuntimeException {
        /**
         * Constructs an exception.
         */
        public RuntimeCopyException(String s) {
            super(s);
        }
    }

    /**
     * Returns the length of this <code>attribute_info</code>
     * structure.
     * The returned value is <code>attribute_length + 6</code>.
     */
    public int length() {
        return 18 + info.length + exceptions.size() * 8
                + org.hotswap.agent.javassist.bytecode.AttributeInfo.getLength(attributes);
    }

    void write(DataOutputStream out) throws IOException {
        out.writeShort(name);           // attribute_name_index
        out.writeInt(length() - 6);     // attribute_length
        out.writeShort(maxStack);       // max_stack
        out.writeShort(maxLocals);      // max_locals
        out.writeInt(info.length);      // code_length
        out.write(info);                // code
        exceptions.write(out);
        out.writeShort(attributes.size());      // attributes_count
        org.hotswap.agent.javassist.bytecode.AttributeInfo.writeAll(attributes, out);        // attributes
    }

    /**
     * This method is not available.
     *
     * @throws java.lang.UnsupportedOperationException always thrown.
     */
    public byte[] get() {
        throw new UnsupportedOperationException("CodeAttribute.get()");
    }

    /**
     * This method is not available.
     *
     * @throws java.lang.UnsupportedOperationException always thrown.
     */
    public void set(byte[] newinfo) {
        throw new UnsupportedOperationException("CodeAttribute.set()");
    }

    void renameClass(String oldname, String newname) {
        org.hotswap.agent.javassist.bytecode.AttributeInfo.renameClass(attributes, oldname, newname);
    }

    void renameClass(Map classnames) {
        org.hotswap.agent.javassist.bytecode.AttributeInfo.renameClass(attributes, classnames);
    }

    void getRefClasses(Map classnames) {
        org.hotswap.agent.javassist.bytecode.AttributeInfo.getRefClasses(attributes, classnames);
    }

    /**
     * Returns the name of the class declaring the method including
     * this code attribute.
     */
    public String getDeclaringClass() {
        org.hotswap.agent.javassist.bytecode.ConstPool cp = getConstPool();
        return cp.getClassName();
    }

    /**
     * Returns <code>max_stack</code>.
     */
    public int getMaxStack() {
        return maxStack;
    }

    /**
     * Sets <code>max_stack</code>.
     */
    public void setMaxStack(int value) {
        maxStack = value;
    }

    /**
     * Computes the maximum stack size and sets <code>max_stack</code>
     * to the computed size.
     *
     * @return the newly computed value of <code>max_stack</code>
     * @throws org.hotswap.agent.javassist.bytecode.BadBytecode if this method fails in computing.
     */
    public int computeMaxStack() throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        maxStack = new CodeAnalyzer(this).computeMaxStack();
        return maxStack;
    }

    /**
     * Returns <code>max_locals</code>.
     */
    public int getMaxLocals() {
        return maxLocals;
    }

    /**
     * Sets <code>max_locals</code>.
     */
    public void setMaxLocals(int value) {
        maxLocals = value;
    }

    /**
     * Returns <code>code_length</code>.
     */
    public int getCodeLength() {
        return info.length;
    }

    /**
     * Returns <code>code[]</code>.
     */
    public byte[] getCode() {
        return info;
    }

    /**
     * Sets <code>code[]</code>.
     */
    void setCode(byte[] newinfo) {
        super.set(newinfo);
    }

    /**
     * Makes a new iterator for reading this code attribute.
     */
    public org.hotswap.agent.javassist.bytecode.CodeIterator iterator() {
        return new org.hotswap.agent.javassist.bytecode.CodeIterator(this);
    }

    /**
     * Returns <code>exception_table[]</code>.
     */
    public org.hotswap.agent.javassist.bytecode.ExceptionTable getExceptionTable() {
        return exceptions;
    }

    /**
     * Returns <code>attributes[]</code>.
     * It returns a list of <code>AttributeInfo</code>.
     * A new element can be added to the returned list
     * and an existing element can be removed from the list.
     *
     * @see org.hotswap.agent.javassist.bytecode.AttributeInfo
     */
    public List getAttributes() {
        return attributes;
    }

    /**
     * Returns the attribute with the specified name.
     * If it is not found, this method returns null.
     *
     * @param name attribute name
     * @return an <code>AttributeInfo</code> object or null.
     */
    public org.hotswap.agent.javassist.bytecode.AttributeInfo getAttribute(String name) {
        return org.hotswap.agent.javassist.bytecode.AttributeInfo.lookup(attributes, name);
    }

    /**
     * Adds a stack map table.  If another copy of stack map table
     * is already contained, the old one is removed.
     *
     * @param smt the stack map table added to this code attribute.
     *            If it is null, a new stack map is not added.
     *            Only the old stack map is removed.
     */
    public void setAttribute(StackMapTable smt) {
        org.hotswap.agent.javassist.bytecode.AttributeInfo.remove(attributes, StackMapTable.tag);
        if (smt != null)
            attributes.add(smt);
    }

    /**
     * Adds a stack map table for J2ME (CLDC).  If another copy of stack map table
     * is already contained, the old one is removed.
     *
     * @param sm the stack map table added to this code attribute.
     *           If it is null, a new stack map is not added.
     *           Only the old stack map is removed.
     * @since 3.12
     */
    public void setAttribute(StackMap sm) {
        org.hotswap.agent.javassist.bytecode.AttributeInfo.remove(attributes, StackMap.tag);
        if (sm != null)
            attributes.add(sm);
    }

    /**
     * Copies code.
     */
    private byte[] copyCode(org.hotswap.agent.javassist.bytecode.ConstPool destCp, Map classnames,
                            org.hotswap.agent.javassist.bytecode.ExceptionTable etable, CodeAttribute destCa)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int len = getCodeLength();
        byte[] newCode = new byte[len];
        destCa.info = newCode;
        LdcEntry ldc = copyCode(this.info, 0, len, this.getConstPool(),
                newCode, destCp, classnames);
        return LdcEntry.doit(newCode, ldc, etable, destCa);
    }

    private static LdcEntry copyCode(byte[] code, int beginPos, int endPos,
                                     org.hotswap.agent.javassist.bytecode.ConstPool srcCp, byte[] newcode,
                                     org.hotswap.agent.javassist.bytecode.ConstPool destCp, Map classnameMap)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int i2, index;
        LdcEntry ldcEntry = null;

        for (int i = beginPos; i < endPos; i = i2) {
            i2 = org.hotswap.agent.javassist.bytecode.CodeIterator.nextOpcode(code, i);
            byte c = code[i];
            newcode[i] = c;
            switch (c & 0xff) {
                case LDC_W:
                case LDC2_W:
                case GETSTATIC:
                case PUTSTATIC:
                case GETFIELD:
                case PUTFIELD:
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case NEW:
                case ANEWARRAY:
                case CHECKCAST:
                case INSTANCEOF:
                    copyConstPoolInfo(i + 1, code, srcCp, newcode, destCp,
                            classnameMap);
                    break;
                case LDC:
                    index = code[i + 1] & 0xff;
                    index = srcCp.copy(index, destCp, classnameMap);
                    if (index < 0x100)
                        newcode[i + 1] = (byte) index;
                    else {
                        newcode[i] = NOP;
                        newcode[i + 1] = NOP;
                        LdcEntry ldc = new LdcEntry();
                        ldc.where = i;
                        ldc.index = index;
                        ldc.next = ldcEntry;
                        ldcEntry = ldc;
                    }
                    break;
                case INVOKEINTERFACE:
                    copyConstPoolInfo(i + 1, code, srcCp, newcode, destCp,
                            classnameMap);
                    newcode[i + 3] = code[i + 3];
                    newcode[i + 4] = code[i + 4];
                    break;
                case INVOKEDYNAMIC:
                    copyConstPoolInfo(i + 1, code, srcCp, newcode, destCp,
                            classnameMap);
                    newcode[i + 3] = 0;
                    newcode[i + 4] = 0;
                    break;
                case MULTIANEWARRAY:
                    copyConstPoolInfo(i + 1, code, srcCp, newcode, destCp,
                            classnameMap);
                    newcode[i + 3] = code[i + 3];
                    break;
                default:
                    while (++i < i2)
                        newcode[i] = code[i];

                    break;
            }
        }

        return ldcEntry;
    }

    private static void copyConstPoolInfo(int i, byte[] code, org.hotswap.agent.javassist.bytecode.ConstPool srcCp,
                                          byte[] newcode, org.hotswap.agent.javassist.bytecode.ConstPool destCp,
                                          Map classnameMap) {
        int index = ((code[i] & 0xff) << 8) | (code[i + 1] & 0xff);
        index = srcCp.copy(index, destCp, classnameMap);
        newcode[i] = (byte) (index >> 8);
        newcode[i + 1] = (byte) index;
    }

    static class LdcEntry {
        LdcEntry next;
        int where;
        int index;

        static byte[] doit(byte[] code, LdcEntry ldc, org.hotswap.agent.javassist.bytecode.ExceptionTable etable,
                           CodeAttribute ca)
                throws org.hotswap.agent.javassist.bytecode.BadBytecode {
            if (ldc != null)
                code = org.hotswap.agent.javassist.bytecode.CodeIterator.changeLdcToLdcW(code, etable, ca, ldc);

            /* The original code was the following:

               while (ldc != null) {
                 int where = ldc.where;
                 code = CodeIterator.insertGapCore0(code, where, 1, false, etable, ca);
                 code[where] = (byte)Opcode.LDC_W;
                 ByteArray.write16bit(ldc.index, code, where + 1);
                 ldc = ldc.next;
               }

               But this code does not support a large method > 32KB.
            */

            return code;
        }
    }

    /**
     * Changes the index numbers of the local variables
     * to append a new parameter.
     * This method does not update <code>LocalVariableAttribute</code>,
     * <code>LocalVariableTypeAttribute</code>,
     * <code>StackMapTable</code>, or <code>StackMap</code>.
     * These attributes must be explicitly updated.
     *
     * @param where the index of the new parameter.
     * @param size  the type size of the new parameter (1 or 2).
     * @see LocalVariableAttribute#shiftIndex(int, int)
     * @see LocalVariableTypeAttribute#shiftIndex(int, int)
     * @see StackMapTable#insertLocal(int, int, int)
     * @see StackMap#insertLocal(int, int, int)
     */
    public void insertLocalVar(int where, int size) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        org.hotswap.agent.javassist.bytecode.CodeIterator ci = iterator();
        while (ci.hasNext())
            shiftIndex(ci, where, size);

        setMaxLocals(getMaxLocals() + size);
    }

    /**
     * @param lessThan If the index of the local variable is
     *                 less than this value, it does not change.
     *                 Otherwise, the index is increased.
     * @param delta    the indexes of the local variables are
     *                 increased by this value.
     */
    private static void shiftIndex(org.hotswap.agent.javassist.bytecode.CodeIterator ci, int lessThan, int delta) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int index = ci.next();
        int opcode = ci.byteAt(index);
        if (opcode < ILOAD)
            return;
        else if (opcode < IASTORE) {
            if (opcode < ILOAD_0) {
                // iload, lload, fload, dload, aload
                shiftIndex8(ci, index, opcode, lessThan, delta);
            } else if (opcode < IALOAD) {
                // iload_0, ..., aload_3
                shiftIndex0(ci, index, opcode, lessThan, delta, ILOAD_0, ILOAD);
            } else if (opcode < ISTORE)
                return;
            else if (opcode < ISTORE_0) {
                // istore, lstore, ...
                shiftIndex8(ci, index, opcode, lessThan, delta);
            } else {
                // istore_0, ..., astore_3
                shiftIndex0(ci, index, opcode, lessThan, delta, ISTORE_0, ISTORE);
            }
        } else if (opcode == IINC) {
            int var = ci.byteAt(index + 1);
            if (var < lessThan)
                return;

            var += delta;
            if (var < 0x100)
                ci.writeByte(var, index + 1);
            else {
                int plus = (byte) ci.byteAt(index + 2);
                int pos = ci.insertExGap(3);
                ci.writeByte(WIDE, pos - 3);
                ci.writeByte(IINC, pos - 2);
                ci.write16bit(var, pos - 1);
                ci.write16bit(plus, pos + 1);
            }
        } else if (opcode == RET)
            shiftIndex8(ci, index, opcode, lessThan, delta);
        else if (opcode == WIDE) {
            int var = ci.u16bitAt(index + 2);
            if (var < lessThan)
                return;

            var += delta;
            ci.write16bit(var, index + 2);
        }
    }

    private static void shiftIndex8(org.hotswap.agent.javassist.bytecode.CodeIterator ci, int index, int opcode,
                                    int lessThan, int delta)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int var = ci.byteAt(index + 1);
        if (var < lessThan)
            return;

        var += delta;
        if (var < 0x100)
            ci.writeByte(var, index + 1);
        else {
            int pos = ci.insertExGap(2);
            ci.writeByte(WIDE, pos - 2);
            ci.writeByte(opcode, pos - 1);
            ci.write16bit(var, pos);
        }
    }

    private static void shiftIndex0(org.hotswap.agent.javassist.bytecode.CodeIterator ci, int index, int opcode,
                                    int lessThan, int delta,
                                    int opcode_i_0, int opcode_i)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int var = (opcode - opcode_i_0) % 4;
        if (var < lessThan)
            return;

        var += delta;
        if (var < 4)
            ci.writeByte(opcode + delta, index);
        else {
            opcode = (opcode - opcode_i_0) / 4 + opcode_i;
            if (var < 0x100) {
                int pos = ci.insertExGap(1);
                ci.writeByte(opcode, pos - 1);
                ci.writeByte(var, pos);
            } else {
                int pos = ci.insertExGap(3);
                ci.writeByte(WIDE, pos - 1);
                ci.writeByte(opcode, pos);
                ci.write16bit(var, pos + 1);
            }
        }
    }
}
