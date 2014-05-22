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

import org.hotswap.agent.javassist.compiler.ast.ASTList;

/**
 * Object creation (<tt>new</tt> expression).
 */
public class NewExpr extends Expr {
    String newTypeName;
    int newPos;

    /**
     * Undocumented constructor.  Do not use; internal-use only.
     */
    protected NewExpr(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator i, org.hotswap.agent.javassist.CtClass declaring,
                      org.hotswap.agent.javassist.bytecode.MethodInfo m, String type, int np) {
        super(pos, i, declaring, m);
        newTypeName = type;
        newPos = np;
    }

    /*
     * Not used
     * 
    private int getNameAndType(ConstPool cp) {
        int pos = currentPos;
        int c = iterator.byteAt(pos);
        int index = iterator.u16bitAt(pos + 1);

        if (c == INVOKEINTERFACE)
            return cp.getInterfaceMethodrefNameAndType(index);
        else
            return cp.getMethodrefNameAndType(index);
    } */

    /**
     * Returns the method or constructor containing the <tt>new</tt>
     * expression represented by this object.
     */
    public org.hotswap.agent.javassist.CtBehavior where() {
        return super.where();
    }

    /**
     * Returns the line number of the source line containing the
     * <tt>new</tt> expression.
     *
     * @return -1       if this information is not available.
     */
    public int getLineNumber() {
        return super.getLineNumber();
    }

    /**
     * Returns the source file containing the <tt>new</tt> expression.
     *
     * @return null     if this information is not available.
     */
    public String getFileName() {
        return super.getFileName();
    }

    /**
     * Returns the class of the created object.
     */
    private org.hotswap.agent.javassist.CtClass getCtClass() throws org.hotswap.agent.javassist.NotFoundException {
        return thisClass.getClassPool().get(newTypeName);
    }

    /**
     * Returns the class name of the created object.
     */
    public String getClassName() {
        return newTypeName;
    }

    /**
     * Get the signature of the constructor
     * <p/>
     * The signature is represented by a character string
     * called method descriptor, which is defined in the JVM specification.
     *
     * @return the signature
     * @see org.hotswap.agent.javassist.CtBehavior#getSignature()
     * @see org.hotswap.agent.javassist.bytecode.Descriptor
     */
    public String getSignature() {
        org.hotswap.agent.javassist.bytecode.ConstPool constPool = getConstPool();
        int methodIndex = iterator.u16bitAt(currentPos + 1);   // constructor
        return constPool.getMethodrefType(methodIndex);
    }

    /**
     * Returns the constructor called for creating the object.
     */
    public org.hotswap.agent.javassist.CtConstructor getConstructor() throws org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.bytecode.ConstPool cp = getConstPool();
        int index = iterator.u16bitAt(currentPos + 1);
        String desc = cp.getMethodrefType(index);
        return getCtClass().getConstructor(desc);
    }

    /**
     * Returns the list of exceptions that the expression may throw.
     * This list includes both the exceptions that the try-catch statements
     * including the expression can catch and the exceptions that
     * the throws declaration allows the method to throw.
     */
    public org.hotswap.agent.javassist.CtClass[] mayThrow() {
        return super.mayThrow();
    }

    /*
     * Returns the parameter types of the constructor.

    public CtClass[] getParameterTypes() throws NotFoundException {
        ConstPool cp = getConstPool();
        int index = iterator.u16bitAt(currentPos + 1);
        String desc = cp.getMethodrefType(index);
        return Descriptor.getParameterTypes(desc, thisClass.getClassPool());
    }
    */

    private int canReplace() throws org.hotswap.agent.javassist.CannotCompileException {
        int op = iterator.byteAt(newPos + 3);
        if (op == DUP)
            return 4;
        else if (op == DUP_X1
                && iterator.byteAt(newPos + 4) == SWAP)
            return 5;
        else
            return 3;   // for Eclipse.  The generated code may include no DUP.
        // throw new CannotCompileException(
        //            "sorry, cannot edit NEW followed by no DUP");
    }

    /**
     * Replaces the <tt>new</tt> expression with the bytecode derived from
     * the given source text.
     * <p/>
     * <p>$0 is available but the value is null.
     *
     * @param statement a Java statement except try-catch.
     */
    public void replace(String statement) throws org.hotswap.agent.javassist.CannotCompileException {
        thisClass.getClassFile();   // to call checkModify().

        final int bytecodeSize = 3;
        int pos = newPos;

        int newIndex = iterator.u16bitAt(pos + 1);

        /* delete the preceding NEW and DUP (or DUP_X1, SWAP) instructions.
         */
        int codeSize = canReplace();
        int end = pos + codeSize;
        for (int i = pos; i < end; ++i)
            iterator.writeByte(NOP, i);

        org.hotswap.agent.javassist.bytecode.ConstPool constPool = getConstPool();
        pos = currentPos;
        int methodIndex = iterator.u16bitAt(pos + 1);   // constructor

        String signature = constPool.getMethodrefType(methodIndex);

        org.hotswap.agent.javassist.compiler.Javac jc = new org.hotswap.agent.javassist.compiler.Javac(thisClass);
        org.hotswap.agent.javassist.ClassPool cp = thisClass.getClassPool();
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = iterator.get();
        try {
            org.hotswap.agent.javassist.CtClass[] params = org.hotswap.agent.javassist.bytecode.Descriptor.getParameterTypes(signature, cp);
            org.hotswap.agent.javassist.CtClass newType = cp.get(newTypeName);
            int paramVar = ca.getMaxLocals();
            jc.recordParams(newTypeName, params,
                    true, paramVar, withinStatic());
            int retVar = jc.recordReturnType(newType, true);
            jc.recordProceed(new ProceedForNew(newType, newIndex,
                    methodIndex));

            /* Is $_ included in the source code?
             */
            checkResultValue(newType, statement);

            org.hotswap.agent.javassist.bytecode.Bytecode bytecode = jc.getBytecode();
            storeStack(params, true, paramVar, bytecode);
            jc.recordLocalVariables(ca, pos);

            bytecode.addConstZero(newType);
            bytecode.addStore(retVar, newType);     // initialize $_

            jc.compileStmnt(statement);
            if (codeSize > 3)   // if the original code includes DUP.
                bytecode.addAload(retVar);

            replace0(pos, bytecode, bytecodeSize);
        } catch (org.hotswap.agent.javassist.compiler.CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException("broken method");
        }
    }

    static class ProceedForNew implements org.hotswap.agent.javassist.compiler.ProceedHandler {
        org.hotswap.agent.javassist.CtClass newType;
        int newIndex, methodIndex;

        ProceedForNew(org.hotswap.agent.javassist.CtClass nt, int ni, int mi) {
            newType = nt;
            newIndex = ni;
            methodIndex = mi;
        }

        public void doit(org.hotswap.agent.javassist.compiler.JvstCodeGen gen, org.hotswap.agent.javassist.bytecode.Bytecode bytecode, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            bytecode.addOpcode(NEW);
            bytecode.addIndex(newIndex);
            bytecode.addOpcode(DUP);
            gen.atMethodCallCore(newType, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, args,
                    false, true, -1, null);
            gen.setType(newType);
        }

        public void setReturnType(org.hotswap.agent.javassist.compiler.JvstTypeChecker c, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            c.atMethodCallCore(newType, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, args);
            c.setType(newType);
        }
    }
}
