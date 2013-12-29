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

/**
 * A <code>catch</code> clause or a <code>finally</code> block.
 */
public class Handler extends Expr {
    private static String EXCEPTION_NAME = "$1";
    private org.hotswap.agent.javassist.bytecode.ExceptionTable etable;
    private int index;

    /**
     * Undocumented constructor.  Do not use; internal-use only.
     */
    protected Handler(org.hotswap.agent.javassist.bytecode.ExceptionTable et, int nth,
                      org.hotswap.agent.javassist.bytecode.CodeIterator it, org.hotswap.agent.javassist.CtClass declaring, org.hotswap.agent.javassist.bytecode.MethodInfo m) {
        super(et.handlerPc(nth), it, declaring, m);
        etable = et;
        index = nth;
    }

    /**
     * Returns the method or constructor containing the catch clause.
     */
    public org.hotswap.agent.javassist.CtBehavior where() {
        return super.where();
    }

    /**
     * Returns the source line number of the catch clause.
     *
     * @return -1       if this information is not available.
     */
    public int getLineNumber() {
        return super.getLineNumber();
    }

    /**
     * Returns the source file containing the catch clause.
     *
     * @return null     if this information is not available.
     */
    public String getFileName() {
        return super.getFileName();
    }

    /**
     * Returns the list of exceptions that the catch clause may throw.
     */
    public org.hotswap.agent.javassist.CtClass[] mayThrow() {
        return super.mayThrow();
    }

    /**
     * Returns the type handled by the catch clause.
     * If this is a <code>finally</code> block, <code>null</code> is returned.
     */
    public org.hotswap.agent.javassist.CtClass getType() throws org.hotswap.agent.javassist.NotFoundException {
        int type = etable.catchType(index);
        if (type == 0)
            return null;
        else {
            org.hotswap.agent.javassist.bytecode.ConstPool cp = getConstPool();
            String name = cp.getClassInfo(type);
            return thisClass.getClassPool().getCtClass(name);
        }
    }

    /**
     * Returns true if this is a <code>finally</code> block.
     */
    public boolean isFinally() {
        return etable.catchType(index) == 0;
    }

    /**
     * This method has not been implemented yet.
     *
     * @param statement a Java statement except try-catch.
     */
    public void replace(String statement) throws org.hotswap.agent.javassist.CannotCompileException {
        throw new RuntimeException("not implemented yet");
    }

    /**
     * Inserts bytecode at the beginning of the catch clause.
     * The caught exception is stored in <code>$1</code>.
     *
     * @param src the source code representing the inserted bytecode.
     *            It must be a single statement or block.
     */
    public void insertBefore(String src) throws org.hotswap.agent.javassist.CannotCompileException {
        edited = true;

        org.hotswap.agent.javassist.bytecode.ConstPool cp = getConstPool();
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = iterator.get();
        org.hotswap.agent.javassist.compiler.Javac jv = new org.hotswap.agent.javassist.compiler.Javac(thisClass);
        org.hotswap.agent.javassist.bytecode.Bytecode b = jv.getBytecode();
        b.setStackDepth(1);
        b.setMaxLocals(ca.getMaxLocals());

        try {
            org.hotswap.agent.javassist.CtClass type = getType();
            int var = jv.recordVariable(type, EXCEPTION_NAME);
            jv.recordReturnType(type, false);
            b.addAstore(var);
            jv.compileStmnt(src);
            b.addAload(var);

            int oldHandler = etable.handlerPc(index);
            b.addOpcode(GOTO);
            b.addIndex(oldHandler - iterator.getCodeLength()
                    - b.currentPc() + 1);

            maxStack = b.getMaxStack();
            maxLocals = b.getMaxLocals();

            int pos = iterator.append(b.get());
            iterator.append(b.getExceptionTable(), pos);
            etable.setHandlerPc(index, pos);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.compiler.CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }
}
