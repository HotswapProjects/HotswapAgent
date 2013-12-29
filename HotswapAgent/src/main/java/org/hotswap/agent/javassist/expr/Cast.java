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
 * Explicit type cast.
 */
public class Cast extends Expr {
    /**
     * Undocumented constructor.  Do not use; internal-use only.
     */
    protected Cast(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator i, org.hotswap.agent.javassist.CtClass declaring, org.hotswap.agent.javassist.bytecode.MethodInfo m) {
        super(pos, i, declaring, m);
    }

    /**
     * Returns the method or constructor containing the type cast
     * expression represented by this object.
     */
    public org.hotswap.agent.javassist.CtBehavior where() {
        return super.where();
    }

    /**
     * Returns the line number of the source line containing the
     * type-cast expression.
     *
     * @return -1       if this information is not available.
     */
    public int getLineNumber() {
        return super.getLineNumber();
    }

    /**
     * Returns the source file containing the type-cast expression.
     *
     * @return null     if this information is not available.
     */
    public String getFileName() {
        return super.getFileName();
    }

    /**
     * Returns the <code>CtClass</code> object representing
     * the type specified by the cast.
     */
    public org.hotswap.agent.javassist.CtClass getType() throws org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.bytecode.ConstPool cp = getConstPool();
        int pos = currentPos;
        int index = iterator.u16bitAt(pos + 1);
        String name = cp.getClassInfo(index);
        return thisClass.getClassPool().getCtClass(name);
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

    /**
     * Replaces the explicit cast operator with the bytecode derived from
     * the given source text.
     * <p/>
     * <p>$0 is available but the value is <code>null</code>.
     *
     * @param statement a Java statement except try-catch.
     */
    public void replace(String statement) throws org.hotswap.agent.javassist.CannotCompileException {
        thisClass.getClassFile();   // to call checkModify().
        org.hotswap.agent.javassist.bytecode.ConstPool constPool = getConstPool();
        int pos = currentPos;
        int index = iterator.u16bitAt(pos + 1);

        org.hotswap.agent.javassist.compiler.Javac jc = new org.hotswap.agent.javassist.compiler.Javac(thisClass);
        org.hotswap.agent.javassist.ClassPool cp = thisClass.getClassPool();
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = iterator.get();

        try {
            org.hotswap.agent.javassist.CtClass[] params
                    = new org.hotswap.agent.javassist.CtClass[]{cp.get(javaLangObject)};
            org.hotswap.agent.javassist.CtClass retType = getType();

            int paramVar = ca.getMaxLocals();
            jc.recordParams(javaLangObject, params, true, paramVar,
                    withinStatic());
            int retVar = jc.recordReturnType(retType, true);
            jc.recordProceed(new ProceedForCast(index, retType));

            /* Is $_ included in the source code?
             */
            checkResultValue(retType, statement);

            org.hotswap.agent.javassist.bytecode.Bytecode bytecode = jc.getBytecode();
            storeStack(params, true, paramVar, bytecode);
            jc.recordLocalVariables(ca, pos);

            bytecode.addConstZero(retType);
            bytecode.addStore(retVar, retType);     // initialize $_

            jc.compileStmnt(statement);
            bytecode.addLoad(retVar, retType);

            replace0(pos, bytecode, 3);
        } catch (org.hotswap.agent.javassist.compiler.CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException("broken method");
        }
    }

    /* <type> $proceed(Object obj)
     */
    static class ProceedForCast implements org.hotswap.agent.javassist.compiler.ProceedHandler {
        int index;
        org.hotswap.agent.javassist.CtClass retType;

        ProceedForCast(int i, org.hotswap.agent.javassist.CtClass t) {
            index = i;
            retType = t;
        }

        public void doit(org.hotswap.agent.javassist.compiler.JvstCodeGen gen, org.hotswap.agent.javassist.bytecode.Bytecode bytecode, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            if (gen.getMethodArgsLength(args) != 1)
                throw new org.hotswap.agent.javassist.compiler.CompileError(org.hotswap.agent.javassist.compiler.Javac.proceedName
                        + "() cannot take more than one parameter "
                        + "for cast");

            gen.atMethodArgs(args, new int[1], new int[1], new String[1]);
            bytecode.addOpcode(CHECKCAST);
            bytecode.addIndex(index);
            gen.setType(retType);
        }

        public void setReturnType(org.hotswap.agent.javassist.compiler.JvstTypeChecker c, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            c.atMethodArgs(args, new int[1], new int[1], new String[1]);
            c.setType(retType);
        }
    }
}
