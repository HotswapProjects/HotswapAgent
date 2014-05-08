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

package org.hotswap.agent.javassist.bytecode.stackmap;

import java.util.ArrayList;

/**
 * Stack map maker.
 */
public class MapMaker extends Tracer {
    /*
    public static void main(String[] args) throws Exception {
        boolean useMain2 = args[0].equals("0");
        if (useMain2 && args.length > 1) {
            main2(args);
            return;
        }

        for (int i = 0; i < args.length; i++)
            main1(args[i]);
    }

    public static void main1(String className) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        //CtClass cc = cp.get(className);
        CtClass cc = cp.makeClass(new java.io.FileInputStream(className));
        System.out.println(className);
        ClassFile cf = cc.getClassFile();
        java.util.List minfos = cf.getMethods();
        for (int i = 0; i < minfos.size(); i++) {
            MethodInfo minfo = (MethodInfo)minfos.get(i);
            CodeAttribute ca = minfo.getCodeAttribute();
            if (ca != null)
                ca.setAttribute(make(cp, minfo));
        }

        cc.writeFile("tmp");
    }

    public static void main2(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        //CtClass cc = cp.get(args[1]);
        CtClass cc = cp.makeClass(new java.io.FileInputStream(args[1]));
        MethodInfo minfo;
        if (args[2].equals("_init_"))
            minfo = cc.getDeclaredConstructors()[0].getMethodInfo();
            // minfo = cc.getClassInitializer().getMethodInfo();
        else
            minfo = cc.getDeclaredMethod(args[2]).getMethodInfo();

        CodeAttribute ca = minfo.getCodeAttribute();
        if (ca == null) {
            System.out.println("abstarct method");
            return;
        }

        TypedBlock[] blocks = TypedBlock.makeBlocks(minfo, ca, false);
        MapMaker mm = new MapMaker(cp, minfo, ca);
        mm.make(blocks, ca.getCode());
        for (int i = 0; i < blocks.length; i++)
            System.out.println(blocks[i]);
    }
    */

    /**
     * Computes the stack map table of the given method and returns it.
     * It returns null if the given method does not have to have a
     * stack map table or it includes JSR.
     */
    public static org.hotswap.agent.javassist.bytecode.StackMapTable make(org.hotswap.agent.javassist.ClassPool classes, org.hotswap.agent.javassist.bytecode.MethodInfo minfo)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = minfo.getCodeAttribute();
        if (ca == null)
            return null;

        org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks;
        try {
            blocks = org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock.makeBlocks(minfo, ca, true);
        } catch (BasicBlock.JsrBytecode e) {
            return null;
        }

        if (blocks == null)
            return null;

        MapMaker mm = new MapMaker(classes, minfo, ca);
        try {
            mm.make(blocks, ca.getCode());
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode bb) {
            throw new org.hotswap.agent.javassist.bytecode.BadBytecode(minfo, bb);
        }

        return mm.toStackMap(blocks);
    }

    /**
     * Computes the stack map table for J2ME.
     * It returns null if the given method does not have to have a
     * stack map table or it includes JSR.
     */
    public static org.hotswap.agent.javassist.bytecode.StackMap make2(org.hotswap.agent.javassist.ClassPool classes, org.hotswap.agent.javassist.bytecode.MethodInfo minfo)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = minfo.getCodeAttribute();
        if (ca == null)
            return null;

        org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks;
        try {
            blocks = org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock.makeBlocks(minfo, ca, true);
        } catch (BasicBlock.JsrBytecode e) {
            return null;
        }

        if (blocks == null)
            return null;

        MapMaker mm = new MapMaker(classes, minfo, ca);
        try {
            mm.make(blocks, ca.getCode());
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode bb) {
            throw new org.hotswap.agent.javassist.bytecode.BadBytecode(minfo, bb);
        }
        return mm.toStackMap2(minfo.getConstPool(), blocks);
    }

    public MapMaker(org.hotswap.agent.javassist.ClassPool classes, org.hotswap.agent.javassist.bytecode.MethodInfo minfo, org.hotswap.agent.javassist.bytecode.CodeAttribute ca) {
        super(classes, minfo.getConstPool(),
                ca.getMaxStack(), ca.getMaxLocals(),
                org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock.getRetType(minfo.getDescriptor()));
    }

    protected MapMaker(MapMaker old) {
        super(old);
    }

    /**
     * Runs an analyzer (Phase 1 and 2).
     */
    void make(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks, byte[] code)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        make(code, blocks[0]);
        findDeadCatchers(code, blocks);
        try {
            fixTypes(code, blocks);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.bytecode.BadBytecode("failed to resolve types", e);
        }
    }

    // Phase 1

    private void make(byte[] code, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock tb)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        copyTypeData(tb.stackTop, tb.stackTypes, stackTypes);
        stackTop = tb.stackTop;
        copyTypeData(tb.localsTypes.length, tb.localsTypes, localsTypes);

        traceException(code, tb.toCatch);

        int pos = tb.position;
        int end = pos + tb.length;
        while (pos < end)
            pos += doOpcode(pos, code);

        traceException(code, tb.toCatch);

        if (tb.exit != null) {
            for (int i = 0; i < tb.exit.length; i++) {
                org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock e = (org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock) tb.exit[i];
                if (e.alreadySet())
                    mergeMap(e, true);
                else {
                    recordStackMap(e);
                    MapMaker maker = new MapMaker(this);
                    maker.make(code, e);
                }
            }
        }
    }

    private void traceException(byte[] code, org.hotswap.agent.javassist.bytecode.stackmap.BasicBlock.Catch handler)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        while (handler != null) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock tb = (org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock) handler.body;
            if (tb.alreadySet()) {
                mergeMap(tb, false);
                if (tb.stackTop < 1)
                    throw new org.hotswap.agent.javassist.bytecode.BadBytecode("bad catch clause: " + handler.typeIndex);

                tb.stackTypes[0] = merge(toExceptionType(handler.typeIndex),
                        tb.stackTypes[0]);
            } else {
                recordStackMap(tb, handler.typeIndex);
                MapMaker maker = new MapMaker(this);
                maker.make(code, tb);
            }

            handler = handler.next;
        }
    }

    private void mergeMap(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock dest, boolean mergeStack) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int n = localsTypes.length;
        for (int i = 0; i < n; i++)
            dest.localsTypes[i] = merge(localsTypes[i], dest.localsTypes[i]);

        if (mergeStack) {
            n = stackTop;
            for (int i = 0; i < n; i++)
                dest.stackTypes[i] = merge(stackTypes[i], dest.stackTypes[i]);
        }
    }

    private org.hotswap.agent.javassist.bytecode.stackmap.TypeData merge(org.hotswap.agent.javassist.bytecode.stackmap.TypeData src, org.hotswap.agent.javassist.bytecode.stackmap.TypeData target) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        if (src == target)
            return target;
        else if (target instanceof org.hotswap.agent.javassist.bytecode.stackmap.TypeData.ClassName
                || target instanceof org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType)  // a parameter
            return target;
        else if (target instanceof org.hotswap.agent.javassist.bytecode.stackmap.TypeData.AbsTypeVar) {
            ((org.hotswap.agent.javassist.bytecode.stackmap.TypeData.AbsTypeVar) target).merge(src);
            return target;
        } else
            throw new RuntimeException("fatal: this should never happen");
    }

    private void recordStackMap(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock target)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] tStackTypes = org.hotswap.agent.javassist.bytecode.stackmap.TypeData.make(stackTypes.length);
        int st = stackTop;
        recordTypeData(st, stackTypes, tStackTypes);
        recordStackMap0(target, st, tStackTypes);
    }

    private void recordStackMap(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock target, int exceptionType)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] tStackTypes = org.hotswap.agent.javassist.bytecode.stackmap.TypeData.make(stackTypes.length);
        tStackTypes[0] = toExceptionType(exceptionType).join();
        recordStackMap0(target, 1, tStackTypes);
    }

    private org.hotswap.agent.javassist.bytecode.stackmap.TypeData.ClassName toExceptionType(int exceptionType) {
        String type;
        if (exceptionType == 0)     // for finally clauses
            type = "java.lang.Throwable";
        else
            type = cpool.getClassInfo(exceptionType);

        return new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.ClassName(type);
    }

    private void recordStackMap0(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock target, int st, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] tStackTypes)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int n = localsTypes.length;
        org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] tLocalsTypes = org.hotswap.agent.javassist.bytecode.stackmap.TypeData.make(n);
        int k = recordTypeData(n, localsTypes, tLocalsTypes);
        target.setStackMap(st, tStackTypes, k, tLocalsTypes);
    }

    protected static int recordTypeData(int n, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] srcTypes, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] destTypes) {
        int k = -1;
        for (int i = 0; i < n; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData t = srcTypes[i];
            destTypes[i] = t.join();
            if (t != org.hotswap.agent.javassist.bytecode.stackmap.TypeTag.TOP)
                k = i + 1;        // t might be long or double.
        }

        return k + 1;
    }

    protected static void copyTypeData(int n, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] srcTypes, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] destTypes) {
        for (int i = 0; i < n; i++)
            destTypes[i] = srcTypes[i];
    }

    // Phase 1.5

    /*
     * Javac may generate an exception handler that catches only an exception
     * thrown within the handler itself.  It is dead code.
     */

    private void findDeadCatchers(byte[] code, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int len = blocks.length;
        for (int i = 0; i < len; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock block = blocks[i];
            if (block.localsTypes == null) { // if block is dead code
                BasicBlock.Catch handler = block.toCatch;
                while (handler != null)
                    if (handler.body == block) {
                        BasicBlock.Catch handler2
                                = new BasicBlock.Catch(block, handler.typeIndex, null);
                        traceException(code, handler2);
                        break;
                    } else
                        handler = handler.next;
            }
        }
    }

    // Phase 2

    /*
     * This method first finds strongly connected components (SCCs)
     * on a graph made by TypeData by Tarjan's algorithm.
     * SCCs are TypeData nodes sharing the same type.
     * Since SCCs are found in the topologically sorted order,
     * their types are also fixed when they are found. 
     */
    private void fixTypes(byte[] code, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks) throws org.hotswap.agent.javassist.NotFoundException, org.hotswap.agent.javassist.bytecode.BadBytecode {
        ArrayList preOrder = new ArrayList();
        int len = blocks.length;
        int index = 0;
        for (int i = 0; i < len; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock block = blocks[i];
            if (block.localsTypes == null)  // if block is dead code
                fixDeadcode(code, block);
            else {
                int n = block.localsTypes.length;
                for (int j = 0; j < n; j++)
                    index = block.localsTypes[j].dfs(preOrder, index, classPool);

                n = block.stackTop;
                for (int j = 0; j < n; j++)
                    index = block.stackTypes[j].dfs(preOrder, index, classPool);
            }
        }
    }

    private void fixDeadcode(byte[] code, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock block) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        int pos = block.position;
        int len = block.length - 3;
        if (len < 0)
            throw new org.hotswap.agent.javassist.bytecode.BadBytecode("dead code detected at " + pos
                    + ".  No stackmap table generated.");

        for (int k = 0; k < len; k++)
            code[pos + k] = org.hotswap.agent.javassist.bytecode.Bytecode.NOP;

        code[pos + len] = (byte) org.hotswap.agent.javassist.bytecode.Bytecode.GOTO;
        org.hotswap.agent.javassist.bytecode.ByteArray.write16bit(-len, code, pos + len + 1);
    }

    // Phase 3

    public org.hotswap.agent.javassist.bytecode.StackMapTable toStackMap(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks) {
        org.hotswap.agent.javassist.bytecode.StackMapTable.Writer writer = new org.hotswap.agent.javassist.bytecode.StackMapTable.Writer(32);
        int n = blocks.length;
        org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock prev = blocks[0];
        int offsetDelta = prev.length;
        if (prev.incoming > 0) {     // the first instruction is a branch target.
            writer.sameFrame(0);
            offsetDelta--;
        }

        for (int i = 1; i < n; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock bb = blocks[i];
            if (isTarget(bb, blocks[i - 1])) {
                bb.resetNumLocals();
                int diffL = stackMapDiff(prev.numLocals, prev.localsTypes,
                        bb.numLocals, bb.localsTypes);
                toStackMapBody(writer, bb, diffL, offsetDelta, prev);
                offsetDelta = bb.length - 1;
                prev = bb;
            } else if (bb.incoming == 0) {
                // dead code.
                writer.sameFrame(offsetDelta);
                offsetDelta = bb.length - 1;
                prev = bb;
            } else
                offsetDelta += bb.length;
        }

        return writer.toStackMapTable(cpool);
    }

    /**
     * Returns true if cur is a branch target.
     */
    private boolean isTarget(org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock cur, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock prev) {
        int in = cur.incoming;
        if (in > 1)
            return true;
        else if (in < 1)
            return false;

        return prev.stop;
    }

    private void toStackMapBody(org.hotswap.agent.javassist.bytecode.StackMapTable.Writer writer, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock bb,
                                int diffL, int offsetDelta, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock prev) {
        // if diffL is -100, two TypeData arrays do not share
        // any elements.

        int stackTop = bb.stackTop;
        if (stackTop == 0) {
            if (diffL == 0) {
                writer.sameFrame(offsetDelta);
                return;
            } else if (0 > diffL && diffL >= -3) {
                writer.chopFrame(offsetDelta, -diffL);
                return;
            } else if (0 < diffL && diffL <= 3) {
                int[] data = new int[diffL];
                int[] tags = fillStackMap(bb.numLocals - prev.numLocals,
                        prev.numLocals, data,
                        bb.localsTypes);
                writer.appendFrame(offsetDelta, tags, data);
                return;
            }
        } else if (stackTop == 1 && diffL == 0) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = bb.stackTypes[0];
            writer.sameLocals(offsetDelta, td.getTypeTag(), td.getTypeData(cpool));
            return;
        } else if (stackTop == 2 && diffL == 0) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = bb.stackTypes[0];
            if (td.is2WordType()) {
                // bb.stackTypes[1] must be TOP.
                writer.sameLocals(offsetDelta, td.getTypeTag(), td.getTypeData(cpool));
                return;
            }
        }

        int[] sdata = new int[stackTop];
        int[] stags = fillStackMap(stackTop, 0, sdata, bb.stackTypes);
        int[] ldata = new int[bb.numLocals];
        int[] ltags = fillStackMap(bb.numLocals, 0, ldata, bb.localsTypes);
        writer.fullFrame(offsetDelta, ltags, ldata, stags, sdata);
    }

    private int[] fillStackMap(int num, int offset, int[] data, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] types) {
        int realNum = diffSize(types, offset, offset + num);
        org.hotswap.agent.javassist.bytecode.ConstPool cp = cpool;
        int[] tags = new int[realNum];
        int j = 0;
        for (int i = 0; i < num; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = types[offset + i];
            tags[j] = td.getTypeTag();
            data[j] = td.getTypeData(cp);
            if (td.is2WordType())
                i++;

            j++;
        }

        return tags;
    }

    private static int stackMapDiff(int oldTdLen, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] oldTd,
                                    int newTdLen, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] newTd) {
        int diff = newTdLen - oldTdLen;
        int len;
        if (diff > 0)
            len = oldTdLen;
        else
            len = newTdLen;

        if (stackMapEq(oldTd, newTd, len))
            if (diff > 0)
                return diffSize(newTd, len, newTdLen);
            else
                return -diffSize(oldTd, len, oldTdLen);
        else
            return -100;
    }

    private static boolean stackMapEq(org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] oldTd, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] newTd, int len) {
        for (int i = 0; i < len; i++) {
            if (!oldTd[i].eq(newTd[i]))
                return false;
        }

        return true;
    }

    private static int diffSize(org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] types, int offset, int len) {
        int num = 0;
        while (offset < len) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = types[offset++];
            num++;
            if (td.is2WordType())
                offset++;
        }

        return num;
    }

    // Phase 3 for J2ME

    public org.hotswap.agent.javassist.bytecode.StackMap toStackMap2(org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock[] blocks) {
        org.hotswap.agent.javassist.bytecode.StackMap.Writer writer = new org.hotswap.agent.javassist.bytecode.StackMap.Writer();
        int n = blocks.length;      // should be > 0
        boolean[] effective = new boolean[n];
        org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock prev = blocks[0];

        // Is the first instruction a branch target?
        effective[0] = prev.incoming > 0;

        int num = effective[0] ? 1 : 0;
        for (int i = 1; i < n; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock bb = blocks[i];
            if (effective[i] = isTarget(bb, blocks[i - 1])) {
                bb.resetNumLocals();
                prev = bb;
                num++;
            }
        }

        if (num == 0)
            return null;

        writer.write16bit(num);
        for (int i = 0; i < n; i++)
            if (effective[i])
                writeStackFrame(writer, cp, blocks[i].position, blocks[i]);

        return writer.toStackMap(cp);
    }

    private void writeStackFrame(org.hotswap.agent.javassist.bytecode.StackMap.Writer writer, org.hotswap.agent.javassist.bytecode.ConstPool cp, int offset, org.hotswap.agent.javassist.bytecode.stackmap.TypedBlock tb) {
        writer.write16bit(offset);
        writeVerifyTypeInfo(writer, cp, tb.localsTypes, tb.numLocals);
        writeVerifyTypeInfo(writer, cp, tb.stackTypes, tb.stackTop);
    }

    private void writeVerifyTypeInfo(org.hotswap.agent.javassist.bytecode.StackMap.Writer writer, org.hotswap.agent.javassist.bytecode.ConstPool cp, org.hotswap.agent.javassist.bytecode.stackmap.TypeData[] types, int num) {
        int numDWord = 0;
        for (int i = 0; i < num; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = types[i];
            if (td != null && td.is2WordType()) {
                numDWord++;
                i++;
            }
        }

        writer.write16bit(num - numDWord);
        for (int i = 0; i < num; i++) {
            org.hotswap.agent.javassist.bytecode.stackmap.TypeData td = types[i];
            writer.writeVerifyTypeInfo(td.getTypeTag(), td.getTypeData(cp));
            if (td.is2WordType())
                i++;
        }
    }
}
