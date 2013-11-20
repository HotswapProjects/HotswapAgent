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
package org.hotswap.agent.javassist.bytecode.analysis;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.*;

import java.util.Iterator;

/**
 * A data-flow analyzer that determines the type state of the stack and local
 * variable table at every reachable instruction in a method. During analysis,
 * bytecode verification is performed in a similar manner to that described
 * in the JVM specification.
 * <p/>
 * <p>Example:</p>
 * <p/>
 * <pre>
 * // Method to analyze
 * public Object doSomething(int x) {
 *     Number n;
 *     if (x < 5) {
 *        n = new Double(0);
 *     } else {
 *        n = new Long(0);
 *     }
 *
 *     return n;
 * }
 *
 * // Which compiles to:
 * // 0:   iload_1
 * // 1:   iconst_5
 * // 2:   if_icmpge   17
 * // 5:   new #18; //class java/lang/Double
 * // 8:   dup
 * // 9:   dconst_0
 * // 10:  invokespecial   #44; //Method java/lang/Double."<init>":(D)V
 * // 13:  astore_2
 * // 14:  goto    26
 * // 17:  new #16; //class java/lang/Long
 * // 20:  dup
 * // 21:  lconst_1
 * // 22:  invokespecial   #47; //Method java/lang/Long."<init>":(J)V
 * // 25:  astore_2
 * // 26:  aload_2
 * // 27:  areturn
 *
 * public void analyzeIt(CtClass clazz, MethodInfo method) {
 *     Analyzer analyzer = new Analyzer();
 *     Frame[] frames = analyzer.analyze(clazz, method);
 *     frames[0].getLocal(0).getCtClass(); // returns clazz;
 *     frames[0].getLocal(1).getCtClass(); // returns java.lang.String
 *     frames[1].peek(); // returns Type.INTEGER
 *     frames[27].peek().getCtClass(); // returns java.lang.Number
 * }
 * </pre>
 *
 * @author Jason T. Greene
 * @see FramePrinter
 */
public class Analyzer implements org.hotswap.agent.javassist.bytecode.Opcode {
    private final SubroutineScanner scanner = new SubroutineScanner();
    private CtClass clazz;
    private ExceptionInfo[] exceptions;
    private org.hotswap.agent.javassist.bytecode.analysis.Frame[] frames;
    private Subroutine[] subroutines;

    private static class ExceptionInfo {
        private int end;
        private int handler;
        private int start;
        private org.hotswap.agent.javassist.bytecode.analysis.Type type;

        private ExceptionInfo(int start, int end, int handler, org.hotswap.agent.javassist.bytecode.analysis.Type type) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.type = type;
        }
    }

    /**
     * Performs data-flow analysis on a method and returns an array, indexed by
     * instruction position, containing the starting frame state of all reachable
     * instructions. Non-reachable code, and illegal code offsets are represented
     * as a null in the frame state array. This can be used to detect dead code.
     * <p/>
     * If the method does not contain code (it is either native or abstract), null
     * is returned.
     *
     * @param clazz  the declaring class of the method
     * @param method the method to analyze
     * @return an array, indexed by instruction position, of the starting frame state,
     * or null if this method doesn't have code
     * @throws BadBytecode if the bytecode does not comply with the JVM specification
     */
    public org.hotswap.agent.javassist.bytecode.analysis.Frame[] analyze(CtClass clazz, MethodInfo method) throws BadBytecode {
        this.clazz = clazz;
        org.hotswap.agent.javassist.bytecode.CodeAttribute codeAttribute = method.getCodeAttribute();
        // Native or Abstract
        if (codeAttribute == null)
            return null;

        int maxLocals = codeAttribute.getMaxLocals();
        int maxStack = codeAttribute.getMaxStack();
        int codeLength = codeAttribute.getCodeLength();

        org.hotswap.agent.javassist.bytecode.CodeIterator iter = codeAttribute.iterator();
        org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue = new org.hotswap.agent.javassist.bytecode.analysis.IntQueue();

        exceptions = buildExceptionInfo(method);
        subroutines = scanner.scan(method);

        org.hotswap.agent.javassist.bytecode.analysis.Executor executor = new org.hotswap.agent.javassist.bytecode.analysis.Executor(clazz.getClassPool(), method.getConstPool());
        frames = new org.hotswap.agent.javassist.bytecode.analysis.Frame[codeLength];
        frames[iter.lookAhead()] = firstFrame(method, maxLocals, maxStack);
        queue.add(iter.next());
        while (!queue.isEmpty()) {
            analyzeNextEntry(method, iter, queue, executor);
        }

        return frames;
    }

    /**
     * Performs data-flow analysis on a method and returns an array, indexed by
     * instruction position, containing the starting frame state of all reachable
     * instructions. Non-reachable code, and illegal code offsets are represented
     * as a null in the frame state array. This can be used to detect dead code.
     * <p/>
     * If the method does not contain code (it is either native or abstract), null
     * is returned.
     *
     * @param method the method to analyze
     * @return an array, indexed by instruction position, of the starting frame state,
     * or null if this method doesn't have code
     * @throws BadBytecode if the bytecode does not comply with the JVM specification
     */
    public org.hotswap.agent.javassist.bytecode.analysis.Frame[] analyze(CtMethod method) throws BadBytecode {
        return analyze(method.getDeclaringClass(), method.getMethodInfo2());
    }

    private void analyzeNextEntry(MethodInfo method, org.hotswap.agent.javassist.bytecode.CodeIterator iter,
                                  org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, org.hotswap.agent.javassist.bytecode.analysis.Executor executor) throws BadBytecode {
        int pos = queue.take();
        iter.move(pos);
        iter.next();

        org.hotswap.agent.javassist.bytecode.analysis.Frame frame = frames[pos].copy();
        Subroutine subroutine = subroutines[pos];

        try {
            executor.execute(method, pos, iter, frame, subroutine);
        } catch (RuntimeException e) {
            throw new BadBytecode(e.getMessage() + "[pos = " + pos + "]", e);
        }

        int opcode = iter.byteAt(pos);

        if (opcode == TABLESWITCH) {
            mergeTableSwitch(queue, pos, iter, frame);
        } else if (opcode == LOOKUPSWITCH) {
            mergeLookupSwitch(queue, pos, iter, frame);
        } else if (opcode == RET) {
            mergeRet(queue, iter, pos, frame, subroutine);
        } else if (org.hotswap.agent.javassist.bytecode.analysis.Util.isJumpInstruction(opcode)) {
            int target = org.hotswap.agent.javassist.bytecode.analysis.Util.getJumpTarget(pos, iter);

            if (org.hotswap.agent.javassist.bytecode.analysis.Util.isJsr(opcode)) {
                // Merge the state before the jsr into the next instruction
                mergeJsr(queue, frames[pos], subroutines[target], pos, lookAhead(iter, pos));
            } else if (!org.hotswap.agent.javassist.bytecode.analysis.Util.isGoto(opcode)) {
                merge(queue, frame, lookAhead(iter, pos));
            }

            merge(queue, frame, target);
        } else if (opcode != ATHROW && !org.hotswap.agent.javassist.bytecode.analysis.Util.isReturn(opcode)) {
            // Can advance to next instruction
            merge(queue, frame, lookAhead(iter, pos));
        }

        // Merge all exceptions that are reachable from this instruction.
        // The redundancy is intentional, since the state must be based
        // on the current instruction frame.
        mergeExceptionHandlers(queue, method, pos, frame);
    }

    private ExceptionInfo[] buildExceptionInfo(MethodInfo method) {
        ConstPool constPool = method.getConstPool();
        org.hotswap.agent.javassist.ClassPool classes = clazz.getClassPool();

        org.hotswap.agent.javassist.bytecode.ExceptionTable table = method.getCodeAttribute().getExceptionTable();
        ExceptionInfo[] exceptions = new ExceptionInfo[table.size()];
        for (int i = 0; i < table.size(); i++) {
            int index = table.catchType(i);
            org.hotswap.agent.javassist.bytecode.analysis.Type type;
            try {
                type = index == 0 ? org.hotswap.agent.javassist.bytecode.analysis.Type.THROWABLE : org.hotswap.agent.javassist.bytecode.analysis.Type.get(classes.get(constPool.getClassInfo(index)));
            } catch (NotFoundException e) {
                throw new IllegalStateException(e.getMessage());
            }

            exceptions[i] = new ExceptionInfo(table.startPc(i), table.endPc(i), table.handlerPc(i), type);
        }

        return exceptions;
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Frame firstFrame(MethodInfo method, int maxLocals, int maxStack) {
        int pos = 0;

        org.hotswap.agent.javassist.bytecode.analysis.Frame first = new org.hotswap.agent.javassist.bytecode.analysis.Frame(maxLocals, maxStack);
        if ((method.getAccessFlags() & AccessFlag.STATIC) == 0) {
            first.setLocal(pos++, org.hotswap.agent.javassist.bytecode.analysis.Type.get(clazz));
        }

        CtClass[] parameters;
        try {
            parameters = Descriptor.getParameterTypes(method.getDescriptor(), clazz.getClassPool());
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < parameters.length; i++) {
            org.hotswap.agent.javassist.bytecode.analysis.Type type = zeroExtend(org.hotswap.agent.javassist.bytecode.analysis.Type.get(parameters[i]));
            first.setLocal(pos++, type);
            if (type.getSize() == 2)
                first.setLocal(pos++, org.hotswap.agent.javassist.bytecode.analysis.Type.TOP);
        }

        return first;
    }

    private int getNext(org.hotswap.agent.javassist.bytecode.CodeIterator iter, int of, int restore) throws BadBytecode {
        iter.move(of);
        iter.next();
        int next = iter.lookAhead();
        iter.move(restore);
        iter.next();

        return next;
    }

    private int lookAhead(org.hotswap.agent.javassist.bytecode.CodeIterator iter, int pos) throws BadBytecode {
        if (!iter.hasNext())
            throw new BadBytecode("Execution falls off end! [pos = " + pos + "]");

        return iter.lookAhead();
    }


    private void merge(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, org.hotswap.agent.javassist.bytecode.analysis.Frame frame, int target) {
        org.hotswap.agent.javassist.bytecode.analysis.Frame old = frames[target];
        boolean changed;

        if (old == null) {
            frames[target] = frame.copy();
            changed = true;
        } else {
            changed = old.merge(frame);
        }

        if (changed) {
            queue.add(target);
        }
    }

    private void mergeExceptionHandlers(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, MethodInfo method, int pos, org.hotswap.agent.javassist.bytecode.analysis.Frame frame) {
        for (int i = 0; i < exceptions.length; i++) {
            ExceptionInfo exception = exceptions[i];

            // Start is inclusive, while end is exclusive!
            if (pos >= exception.start && pos < exception.end) {
                org.hotswap.agent.javassist.bytecode.analysis.Frame newFrame = frame.copy();
                newFrame.clearStack();
                newFrame.push(exception.type);
                merge(queue, newFrame, exception.handler);
            }
        }
    }

    private void mergeJsr(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, org.hotswap.agent.javassist.bytecode.analysis.Frame frame, Subroutine sub, int pos, int next) throws BadBytecode {
        if (sub == null)
            throw new BadBytecode("No subroutine at jsr target! [pos = " + pos + "]");

        org.hotswap.agent.javassist.bytecode.analysis.Frame old = frames[next];
        boolean changed = false;

        if (old == null) {
            old = frames[next] = frame.copy();
            changed = true;
        } else {
            for (int i = 0; i < frame.localsLength(); i++) {
                // Skip everything accessed by a subroutine, mergeRet must handle this
                if (!sub.isAccessed(i)) {
                    org.hotswap.agent.javassist.bytecode.analysis.Type oldType = old.getLocal(i);
                    org.hotswap.agent.javassist.bytecode.analysis.Type newType = frame.getLocal(i);
                    if (oldType == null) {
                        old.setLocal(i, newType);
                        changed = true;
                        continue;
                    }

                    newType = oldType.merge(newType);
                    // Always set the type, in case a multi-type switched to a standard type.
                    old.setLocal(i, newType);
                    if (!newType.equals(oldType) || newType.popChanged())
                        changed = true;
                }
            }
        }

        if (!old.isJsrMerged()) {
            old.setJsrMerged(true);
            changed = true;
        }

        if (changed && old.isRetMerged())
            queue.add(next);

    }

    private void mergeLookupSwitch(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, org.hotswap.agent.javassist.bytecode.analysis.Frame frame) throws BadBytecode {
        int index = (pos & ~3) + 4;
        // default
        merge(queue, frame, pos + iter.s32bitAt(index));
        int npairs = iter.s32bitAt(index += 4);
        int end = npairs * 8 + (index += 4);

        // skip "match"
        for (index += 4; index < end; index += 8) {
            int target = iter.s32bitAt(index) + pos;
            merge(queue, frame, target);
        }
    }

    private void mergeRet(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, org.hotswap.agent.javassist.bytecode.CodeIterator iter, int pos, org.hotswap.agent.javassist.bytecode.analysis.Frame frame, Subroutine subroutine) throws BadBytecode {
        if (subroutine == null)
            throw new BadBytecode("Ret on no subroutine! [pos = " + pos + "]");

        Iterator callerIter = subroutine.callers().iterator();
        while (callerIter.hasNext()) {
            int caller = ((Integer) callerIter.next()).intValue();
            int returnLoc = getNext(iter, caller, pos);
            boolean changed = false;

            org.hotswap.agent.javassist.bytecode.analysis.Frame old = frames[returnLoc];
            if (old == null) {
                old = frames[returnLoc] = frame.copyStack();
                changed = true;
            } else {
                changed = old.mergeStack(frame);
            }

            for (Iterator i = subroutine.accessed().iterator(); i.hasNext(); ) {
                int index = ((Integer) i.next()).intValue();
                org.hotswap.agent.javassist.bytecode.analysis.Type oldType = old.getLocal(index);
                org.hotswap.agent.javassist.bytecode.analysis.Type newType = frame.getLocal(index);
                if (oldType != newType) {
                    old.setLocal(index, newType);
                    changed = true;
                }
            }

            if (!old.isRetMerged()) {
                old.setRetMerged(true);
                changed = true;
            }

            if (changed && old.isJsrMerged())
                queue.add(returnLoc);
        }
    }


    private void mergeTableSwitch(org.hotswap.agent.javassist.bytecode.analysis.IntQueue queue, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, org.hotswap.agent.javassist.bytecode.analysis.Frame frame) throws BadBytecode {
        // Skip 4 byte alignment padding
        int index = (pos & ~3) + 4;
        // default
        merge(queue, frame, pos + iter.s32bitAt(index));
        int low = iter.s32bitAt(index += 4);
        int high = iter.s32bitAt(index += 4);
        int end = (high - low + 1) * 4 + (index += 4);

        // Offset table
        for (; index < end; index += 4) {
            int target = iter.s32bitAt(index) + pos;
            merge(queue, frame, target);
        }
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type zeroExtend(org.hotswap.agent.javassist.bytecode.analysis.Type type) {
        if (type == org.hotswap.agent.javassist.bytecode.analysis.Type.SHORT || type == org.hotswap.agent.javassist.bytecode.analysis.Type.BYTE || type == org.hotswap.agent.javassist.bytecode.analysis.Type.CHAR || type == org.hotswap.agent.javassist.bytecode.analysis.Type.BOOLEAN)
            return org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER;

        return type;
    }
}
