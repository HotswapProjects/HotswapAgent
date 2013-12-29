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

package org.hotswap.agent.javassist.expr;

import org.hotswap.agent.javassist.bytecode.AccessFlag;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Expression.
 */
public abstract class Expr implements org.hotswap.agent.javassist.bytecode.Opcode {
    int currentPos;
    org.hotswap.agent.javassist.bytecode.CodeIterator iterator;
    org.hotswap.agent.javassist.CtClass thisClass;
    org.hotswap.agent.javassist.bytecode.MethodInfo thisMethod;
    boolean edited;
    int maxLocals, maxStack;

    static final String javaLangObject = "java.lang.Object";

    /**
     * Undocumented constructor. Do not use; internal-use only.
     */
    protected Expr(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator i, org.hotswap.agent.javassist.CtClass declaring, org.hotswap.agent.javassist.bytecode.MethodInfo m) {
        currentPos = pos;
        iterator = i;
        thisClass = declaring;
        thisMethod = m;
    }

    /**
     * Returns the class that declares the method enclosing
     * this expression.
     *
     * @since 3.7
     */
    public org.hotswap.agent.javassist.CtClass getEnclosingClass() {
        return thisClass;
    }

    protected final org.hotswap.agent.javassist.bytecode.ConstPool getConstPool() {
        return thisMethod.getConstPool();
    }

    protected final boolean edited() {
        return edited;
    }

    protected final int locals() {
        return maxLocals;
    }

    protected final int stack() {
        return maxStack;
    }

    /**
     * Returns true if this method is static.
     */
    protected final boolean withinStatic() {
        return (thisMethod.getAccessFlags() & AccessFlag.STATIC) != 0;
    }

    /**
     * Returns the constructor or method containing the expression.
     */
    public org.hotswap.agent.javassist.CtBehavior where() {
        org.hotswap.agent.javassist.bytecode.MethodInfo mi = thisMethod;
        org.hotswap.agent.javassist.CtBehavior[] cb = thisClass.getDeclaredBehaviors();
        for (int i = cb.length - 1; i >= 0; --i)
            if (cb[i].getMethodInfo2() == mi)
                return cb[i];

        org.hotswap.agent.javassist.CtConstructor init = thisClass.getClassInitializer();
        if (init != null && init.getMethodInfo2() == mi)
            return init;

        /* getDeclaredBehaviors() returns a list of methods/constructors.
         * Although the list is cached in a CtClass object, it might be
         * recreated for some reason.  Thus, the member name and the signature
         * must be also checked.
         */
        for (int i = cb.length - 1; i >= 0; --i) {
            if (thisMethod.getName().equals(cb[i].getMethodInfo2().getName())
                    && thisMethod.getDescriptor()
                    .equals(cb[i].getMethodInfo2().getDescriptor())) {
                return cb[i];
            }
        }

        throw new RuntimeException("fatal: not found");
    }

    /**
     * Returns the list of exceptions that the expression may throw. This list
     * includes both the exceptions that the try-catch statements including the
     * expression can catch and the exceptions that the throws declaration
     * allows the method to throw.
     */
    public org.hotswap.agent.javassist.CtClass[] mayThrow() {
        org.hotswap.agent.javassist.ClassPool pool = thisClass.getClassPool();
        org.hotswap.agent.javassist.bytecode.ConstPool cp = thisMethod.getConstPool();
        LinkedList list = new LinkedList();
        try {
            org.hotswap.agent.javassist.bytecode.CodeAttribute ca = thisMethod.getCodeAttribute();
            org.hotswap.agent.javassist.bytecode.ExceptionTable et = ca.getExceptionTable();
            int pos = currentPos;
            int n = et.size();
            for (int i = 0; i < n; ++i)
                if (et.startPc(i) <= pos && pos < et.endPc(i)) {
                    int t = et.catchType(i);
                    if (t > 0)
                        try {
                            addClass(list, pool.get(cp.getClassInfo(t)));
                        } catch (org.hotswap.agent.javassist.NotFoundException e) {
                        }
                }
        } catch (NullPointerException e) {
        }

        org.hotswap.agent.javassist.bytecode.ExceptionsAttribute ea = thisMethod.getExceptionsAttribute();
        if (ea != null) {
            String[] exceptions = ea.getExceptions();
            if (exceptions != null) {
                int n = exceptions.length;
                for (int i = 0; i < n; ++i)
                    try {
                        addClass(list, pool.get(exceptions[i]));
                    } catch (org.hotswap.agent.javassist.NotFoundException e) {
                    }
            }
        }

        return (org.hotswap.agent.javassist.CtClass[]) list.toArray(new org.hotswap.agent.javassist.CtClass[list.size()]);
    }

    private static void addClass(LinkedList list, org.hotswap.agent.javassist.CtClass c) {
        Iterator it = list.iterator();
        while (it.hasNext())
            if (it.next() == c)
                return;

        list.add(c);
    }

    /**
     * Returns the index of the bytecode corresponding to the expression. It is
     * the index into the byte array containing the Java bytecode that
     * implements the method.
     */
    public int indexOfBytecode() {
        return currentPos;
    }

    /**
     * Returns the line number of the source line containing the expression.
     *
     * @return -1 if this information is not available.
     */
    public int getLineNumber() {
        return thisMethod.getLineNumber(currentPos);
    }

    /**
     * Returns the source file containing the expression.
     *
     * @return null if this information is not available.
     */
    public String getFileName() {
        org.hotswap.agent.javassist.bytecode.ClassFile cf = thisClass.getClassFile2();
        if (cf == null)
            return null;
        else
            return cf.getSourceFile();
    }

    static final boolean checkResultValue(org.hotswap.agent.javassist.CtClass retType, String prog)
            throws org.hotswap.agent.javassist.CannotCompileException {
        /*
         * Is $_ included in the source code?
         */
        boolean hasIt = (prog.indexOf(org.hotswap.agent.javassist.compiler.Javac.resultVarName) >= 0);
        if (!hasIt && retType != org.hotswap.agent.javassist.CtClass.voidType)
            throw new org.hotswap.agent.javassist.CannotCompileException(
                    "the resulting value is not stored in "
                            + org.hotswap.agent.javassist.compiler.Javac.resultVarName);

        return hasIt;
    }

    /*
     * If isStaticCall is true, null is assigned to $0. So $0 must be declared
     * by calling Javac.recordParams().
     * 
     * After executing this method, the current stack depth might be less than
     * 0.
     */
    static final void storeStack(org.hotswap.agent.javassist.CtClass[] params, boolean isStaticCall,
                                 int regno, org.hotswap.agent.javassist.bytecode.Bytecode bytecode) {
        storeStack0(0, params.length, params, regno + 1, bytecode);
        if (isStaticCall)
            bytecode.addOpcode(ACONST_NULL);

        bytecode.addAstore(regno);
    }

    private static void storeStack0(int i, int n, org.hotswap.agent.javassist.CtClass[] params, int regno,
                                    org.hotswap.agent.javassist.bytecode.Bytecode bytecode) {
        if (i >= n)
            return;
        else {
            org.hotswap.agent.javassist.CtClass c = params[i];
            int size;
            if (c instanceof org.hotswap.agent.javassist.CtPrimitiveType)
                size = ((org.hotswap.agent.javassist.CtPrimitiveType) c).getDataSize();
            else
                size = 1;

            storeStack0(i + 1, n, params, regno + size, bytecode);
            bytecode.addStore(regno, c);
        }
    }

    // The implementation of replace() should call thisClass.checkModify()
    // so that isModify() will return true.  Otherwise, thisClass.classfile
    // might be released during compilation and the compiler might generate
    // bytecode with a wrong copy of ConstPool.

    /**
     * Replaces this expression with the bytecode derived from
     * the given source text.
     *
     * @param statement a Java statement except try-catch.
     */
    public abstract void replace(String statement) throws org.hotswap.agent.javassist.CannotCompileException;

    /**
     * Replaces this expression with the bytecode derived from
     * the given source text and <code>ExprEditor</code>.
     *
     * @param statement a Java statement except try-catch.
     * @param recursive if not null, the substituted bytecode
     *                  is recursively processed by the given
     *                  <code>ExprEditor</code>.
     * @since 3.1
     */
    public void replace(String statement, ExprEditor recursive)
            throws org.hotswap.agent.javassist.CannotCompileException {
        replace(statement);
        if (recursive != null)
            runEditor(recursive, iterator);
    }

    protected void replace0(int pos, org.hotswap.agent.javassist.bytecode.Bytecode bytecode, int size)
            throws org.hotswap.agent.javassist.bytecode.BadBytecode {
        byte[] code = bytecode.get();
        edited = true;
        int gap = code.length - size;
        for (int i = 0; i < size; ++i)
            iterator.writeByte(NOP, pos + i);

        if (gap > 0)
            pos = iterator.insertGapAt(pos, gap, false).position;

        iterator.write(code, pos);
        iterator.insert(bytecode.getExceptionTable(), pos);
        maxLocals = bytecode.getMaxLocals();
        maxStack = bytecode.getMaxStack();
    }

    protected void runEditor(ExprEditor ed, org.hotswap.agent.javassist.bytecode.CodeIterator oldIterator)
            throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.CodeAttribute codeAttr = oldIterator.get();
        int orgLocals = codeAttr.getMaxLocals();
        int orgStack = codeAttr.getMaxStack();
        int newLocals = locals();
        codeAttr.setMaxStack(stack());
        codeAttr.setMaxLocals(newLocals);
        ExprEditor.LoopContext context
                = new ExprEditor.LoopContext(newLocals);
        int size = oldIterator.getCodeLength();
        int endPos = oldIterator.lookAhead();
        oldIterator.move(currentPos);
        if (ed.doit(thisClass, thisMethod, context, oldIterator, endPos))
            edited = true;

        oldIterator.move(endPos + oldIterator.getCodeLength() - size);
        codeAttr.setMaxLocals(orgLocals);
        codeAttr.setMaxStack(orgStack);
        maxLocals = context.maxLocals;
        maxStack += context.maxStack;
    }
}
