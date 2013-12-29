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
 * Array creation.
 * <p/>
 * <p>This class does not provide methods for obtaining the initial
 * values of array elements.
 */
public class NewArray extends Expr {
    int opcode;

    protected NewArray(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator i, org.hotswap.agent.javassist.CtClass declaring,
                       org.hotswap.agent.javassist.bytecode.MethodInfo m, int op) {
        super(pos, i, declaring, m);
        opcode = op;
    }

    /**
     * Returns the method or constructor containing the array creation
     * represented by this object.
     */
    public org.hotswap.agent.javassist.CtBehavior where() {
        return super.where();
    }

    /**
     * Returns the line number of the source line containing the
     * array creation.
     *
     * @return -1       if this information is not available.
     */
    public int getLineNumber() {
        return super.getLineNumber();
    }

    /**
     * Returns the source file containing the array creation.
     *
     * @return null     if this information is not available.
     */
    public String getFileName() {
        return super.getFileName();
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
     * Returns the type of array components.  If the created array is
     * a two-dimensional array of <tt>int</tt>,
     * the type returned by this method is
     * not <tt>int[]</tt> but <tt>int</tt>.
     */
    public org.hotswap.agent.javassist.CtClass getComponentType() throws org.hotswap.agent.javassist.NotFoundException {
        if (opcode == NEWARRAY) {
            int atype = iterator.byteAt(currentPos + 1);
            return getPrimitiveType(atype);
        } else if (opcode == ANEWARRAY
                || opcode == MULTIANEWARRAY) {
            int index = iterator.u16bitAt(currentPos + 1);
            String desc = getConstPool().getClassInfo(index);
            int dim = org.hotswap.agent.javassist.bytecode.Descriptor.arrayDimension(desc);
            desc = org.hotswap.agent.javassist.bytecode.Descriptor.toArrayComponent(desc, dim);
            return org.hotswap.agent.javassist.bytecode.Descriptor.toCtClass(desc, thisClass.getClassPool());
        } else
            throw new RuntimeException("bad opcode: " + opcode);
    }

    org.hotswap.agent.javassist.CtClass getPrimitiveType(int atype) {
        switch (atype) {
            case T_BOOLEAN:
                return org.hotswap.agent.javassist.CtClass.booleanType;
            case T_CHAR:
                return org.hotswap.agent.javassist.CtClass.charType;
            case T_FLOAT:
                return org.hotswap.agent.javassist.CtClass.floatType;
            case T_DOUBLE:
                return org.hotswap.agent.javassist.CtClass.doubleType;
            case T_BYTE:
                return org.hotswap.agent.javassist.CtClass.byteType;
            case T_SHORT:
                return org.hotswap.agent.javassist.CtClass.shortType;
            case T_INT:
                return org.hotswap.agent.javassist.CtClass.intType;
            case T_LONG:
                return org.hotswap.agent.javassist.CtClass.longType;
            default:
                throw new RuntimeException("bad atype: " + atype);
        }
    }

    /**
     * Returns the dimension of the created array.
     */
    public int getDimension() {
        if (opcode == NEWARRAY)
            return 1;
        else if (opcode == ANEWARRAY
                || opcode == MULTIANEWARRAY) {
            int index = iterator.u16bitAt(currentPos + 1);
            String desc = getConstPool().getClassInfo(index);
            return org.hotswap.agent.javassist.bytecode.Descriptor.arrayDimension(desc)
                    + (opcode == ANEWARRAY ? 1 : 0);
        } else
            throw new RuntimeException("bad opcode: " + opcode);
    }

    /**
     * Returns the number of dimensions of arrays to be created.
     * If the opcode is multianewarray, this method returns the second
     * operand.  Otherwise, it returns 1.
     */
    public int getCreatedDimensions() {
        if (opcode == MULTIANEWARRAY)
            return iterator.byteAt(currentPos + 3);
        else
            return 1;
    }

    /**
     * Replaces the array creation with the bytecode derived from
     * the given source text.
     * <p/>
     * <p>$0 is available even if the called method is static.
     * If the field access is writing, $_ is available but the value
     * of $_ is ignored.
     *
     * @param statement a Java statement except try-catch.
     */
    public void replace(String statement) throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            replace2(statement);
        } catch (org.hotswap.agent.javassist.compiler.CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException("broken method");
        }
    }

    private void replace2(String statement)
            throws org.hotswap.agent.javassist.compiler.CompileError, org.hotswap.agent.javassist.NotFoundException, org.hotswap.agent.javassist.bytecode.BadBytecode,
            org.hotswap.agent.javassist.CannotCompileException {
        thisClass.getClassFile();   // to call checkModify().
        org.hotswap.agent.javassist.bytecode.ConstPool constPool = getConstPool();
        int pos = currentPos;
        org.hotswap.agent.javassist.CtClass retType;
        int codeLength;
        int index = 0;
        int dim = 1;
        String desc;
        if (opcode == NEWARRAY) {
            index = iterator.byteAt(currentPos + 1);    // atype
            org.hotswap.agent.javassist.CtPrimitiveType cpt = (org.hotswap.agent.javassist.CtPrimitiveType) getPrimitiveType(index);
            desc = "[" + cpt.getDescriptor();
            codeLength = 2;
        } else if (opcode == ANEWARRAY) {
            index = iterator.u16bitAt(pos + 1);
            desc = constPool.getClassInfo(index);
            if (desc.startsWith("["))
                desc = "[" + desc;
            else
                desc = "[L" + desc + ";";

            codeLength = 3;
        } else if (opcode == MULTIANEWARRAY) {
            index = iterator.u16bitAt(currentPos + 1);
            desc = constPool.getClassInfo(index);
            dim = iterator.byteAt(currentPos + 3);
            codeLength = 4;
        } else
            throw new RuntimeException("bad opcode: " + opcode);

        retType = org.hotswap.agent.javassist.bytecode.Descriptor.toCtClass(desc, thisClass.getClassPool());

        org.hotswap.agent.javassist.compiler.Javac jc = new org.hotswap.agent.javassist.compiler.Javac(thisClass);
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = iterator.get();

        org.hotswap.agent.javassist.CtClass[] params = new org.hotswap.agent.javassist.CtClass[dim];
        for (int i = 0; i < dim; ++i)
            params[i] = org.hotswap.agent.javassist.CtClass.intType;

        int paramVar = ca.getMaxLocals();
        jc.recordParams(javaLangObject, params,
                true, paramVar, withinStatic());

        /* Is $_ included in the source code?
         */
        checkResultValue(retType, statement);
        int retVar = jc.recordReturnType(retType, true);
        jc.recordProceed(new ProceedForArray(retType, opcode, index, dim));

        org.hotswap.agent.javassist.bytecode.Bytecode bytecode = jc.getBytecode();
        storeStack(params, true, paramVar, bytecode);
        jc.recordLocalVariables(ca, pos);

        bytecode.addOpcode(ACONST_NULL);        // initialize $_
        bytecode.addAstore(retVar);

        jc.compileStmnt(statement);
        bytecode.addAload(retVar);

        replace0(pos, bytecode, codeLength);
    }

    /* <array type> $proceed(<dim> ..)
     */
    static class ProceedForArray implements org.hotswap.agent.javassist.compiler.ProceedHandler {
        org.hotswap.agent.javassist.CtClass arrayType;
        int opcode;
        int index, dimension;

        ProceedForArray(org.hotswap.agent.javassist.CtClass type, int op, int i, int dim) {
            arrayType = type;
            opcode = op;
            index = i;
            dimension = dim;
        }

        public void doit(org.hotswap.agent.javassist.compiler.JvstCodeGen gen, org.hotswap.agent.javassist.bytecode.Bytecode bytecode, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            int num = gen.getMethodArgsLength(args);
            if (num != dimension)
                throw new org.hotswap.agent.javassist.compiler.CompileError(org.hotswap.agent.javassist.compiler.Javac.proceedName
                        + "() with a wrong number of parameters");

            gen.atMethodArgs(args, new int[num],
                    new int[num], new String[num]);
            bytecode.addOpcode(opcode);
            if (opcode == ANEWARRAY)
                bytecode.addIndex(index);
            else if (opcode == NEWARRAY)
                bytecode.add(index);
            else /* if (opcode == Opcode.MULTIANEWARRAY) */ {
                bytecode.addIndex(index);
                bytecode.add(dimension);
                bytecode.growStack(1 - dimension);
            }

            gen.setType(arrayType);
        }

        public void setReturnType(org.hotswap.agent.javassist.compiler.JvstTypeChecker c, ASTList args)
                throws org.hotswap.agent.javassist.compiler.CompileError {
            c.setType(arrayType);
        }
    }
}
