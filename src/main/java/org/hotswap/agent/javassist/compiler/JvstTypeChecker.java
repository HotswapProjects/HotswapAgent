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

package org.hotswap.agent.javassist.compiler;

/* Type checker accepting extended Java syntax for Javassist.
 */

public class JvstTypeChecker extends TypeChecker {
    private org.hotswap.agent.javassist.compiler.JvstCodeGen codeGen;

    public JvstTypeChecker(org.hotswap.agent.javassist.CtClass cc, org.hotswap.agent.javassist.ClassPool cp, org.hotswap.agent.javassist.compiler.JvstCodeGen gen) {
        super(cc, cp);
        codeGen = gen;
    }

    /* If the type of the expression compiled last is void,
     * add ACONST_NULL and change exprType, arrayDim, className.
     */
    public void addNullIfVoid() {
        if (exprType == VOID) {
            exprType = CLASS;
            arrayDim = 0;
            className = jvmJavaLangObject;
        }
    }

    /* To support $args, $sig, and $type.
     * $args is an array of parameter list.
     */
    public void atMember(org.hotswap.agent.javassist.compiler.ast.Member mem) throws org.hotswap.agent.javassist.compiler.CompileError {
        String name = mem.get();
        if (name.equals(codeGen.paramArrayName)) {
            exprType = CLASS;
            arrayDim = 1;
            className = jvmJavaLangObject;
        } else if (name.equals(org.hotswap.agent.javassist.compiler.JvstCodeGen.sigName)) {
            exprType = CLASS;
            arrayDim = 1;
            className = "java/lang/Class";
        } else if (name.equals(org.hotswap.agent.javassist.compiler.JvstCodeGen.dollarTypeName)
                || name.equals(org.hotswap.agent.javassist.compiler.JvstCodeGen.clazzName)) {
            exprType = CLASS;
            arrayDim = 0;
            className = "java/lang/Class";
        } else
            super.atMember(mem);
    }

    protected void atFieldAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree left, org.hotswap.agent.javassist.compiler.ast.ASTree right)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        if (left instanceof org.hotswap.agent.javassist.compiler.ast.Member
                && ((org.hotswap.agent.javassist.compiler.ast.Member) left).get().equals(codeGen.paramArrayName)) {
            right.accept(this);
            org.hotswap.agent.javassist.CtClass[] params = codeGen.paramTypeList;
            if (params == null)
                return;

            int n = params.length;
            for (int i = 0; i < n; ++i)
                compileUnwrapValue(params[i]);
        } else
            super.atFieldAssign(expr, op, left, right);
    }

    public void atCastExpr(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList classname = expr.getClassName();
        if (classname != null && expr.getArrayDim() == 0) {
            org.hotswap.agent.javassist.compiler.ast.ASTree p = classname.head();
            if (p instanceof org.hotswap.agent.javassist.compiler.ast.Symbol && classname.tail() == null) {
                String typename = ((org.hotswap.agent.javassist.compiler.ast.Symbol) p).get();
                if (typename.equals(codeGen.returnCastName)) {
                    atCastToRtype(expr);
                    return;
                } else if (typename.equals(org.hotswap.agent.javassist.compiler.JvstCodeGen.wrapperCastName)) {
                    atCastToWrapper(expr);
                    return;
                }
            }
        }

        super.atCastExpr(expr);
    }

    /**
     * Inserts a cast operator to the return type.
     * If the return type is void, this does nothing.
     */
    protected void atCastToRtype(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtClass returnType = codeGen.returnType;
        expr.getOprand().accept(this);
        if (exprType == VOID || CodeGen.isRefType(exprType) || arrayDim > 0)
            compileUnwrapValue(returnType);
        else if (returnType instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) returnType;
            int destType = org.hotswap.agent.javassist.compiler.MemberResolver.descToType(pt.getDescriptor());
            exprType = destType;
            arrayDim = 0;
            className = null;
        }
    }

    protected void atCastToWrapper(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        expr.getOprand().accept(this);
        if (CodeGen.isRefType(exprType) || arrayDim > 0)
            return;     // Object type.  do nothing.

        org.hotswap.agent.javassist.CtClass clazz = resolver.lookupClass(exprType, arrayDim, className);
        if (clazz instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
            exprType = CLASS;
            arrayDim = 0;
            className = jvmJavaLangObject;
        }
    }

    /* Delegates to a ProcHandler object if the method call is
     * $proceed().  It may process $cflow().
     */
    public void atCallExpr(org.hotswap.agent.javassist.compiler.ast.CallExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree method = expr.oprand1();
        if (method instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            String name = ((org.hotswap.agent.javassist.compiler.ast.Member) method).get();
            if (codeGen.procHandler != null
                    && name.equals(codeGen.proceedName)) {
                codeGen.procHandler.setReturnType(this,
                        (org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2());
                return;
            } else if (name.equals(org.hotswap.agent.javassist.compiler.JvstCodeGen.cflowName)) {
                atCflow((org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2());
                return;
            }
        }

        super.atCallExpr(expr);
    }

    /* To support $cflow().
     */
    protected void atCflow(org.hotswap.agent.javassist.compiler.ast.ASTList cname) throws org.hotswap.agent.javassist.compiler.CompileError {
        exprType = INT;
        arrayDim = 0;
        className = null;
    }

    /* To support $$.  ($$) is equivalent to ($1, ..., $n).
     * It can be used only as a parameter list of method call.
     */
    public boolean isParamListName(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        if (codeGen.paramTypeList != null
                && args != null && args.tail() == null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree left = args.head();
            return (left instanceof org.hotswap.agent.javassist.compiler.ast.Member
                    && ((org.hotswap.agent.javassist.compiler.ast.Member) left).get().equals(codeGen.paramListName));
        } else
            return false;
    }

    public int getMethodArgsLength(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        String pname = codeGen.paramListName;
        int n = 0;
        while (args != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree a = args.head();
            if (a instanceof org.hotswap.agent.javassist.compiler.ast.Member && ((org.hotswap.agent.javassist.compiler.ast.Member) a).get().equals(pname)) {
                if (codeGen.paramTypeList != null)
                    n += codeGen.paramTypeList.length;
            } else
                ++n;

            args = args.tail();
        }

        return n;
    }

    public void atMethodArgs(org.hotswap.agent.javassist.compiler.ast.ASTList args, int[] types, int[] dims,
                             String[] cnames) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtClass[] params = codeGen.paramTypeList;
        String pname = codeGen.paramListName;
        int i = 0;
        while (args != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree a = args.head();
            if (a instanceof org.hotswap.agent.javassist.compiler.ast.Member && ((org.hotswap.agent.javassist.compiler.ast.Member) a).get().equals(pname)) {
                if (params != null) {
                    int n = params.length;
                    for (int k = 0; k < n; ++k) {
                        org.hotswap.agent.javassist.CtClass p = params[k];
                        setType(p);
                        types[i] = exprType;
                        dims[i] = arrayDim;
                        cnames[i] = className;
                        ++i;
                    }
                }
            } else {
                a.accept(this);
                types[i] = exprType;
                dims[i] = arrayDim;
                cnames[i] = className;
                ++i;
            }

            args = args.tail();
        }
    }

    /* called by Javac#recordSpecialProceed().
     */
    void compileInvokeSpecial(org.hotswap.agent.javassist.compiler.ast.ASTree target, String classname,
                              String methodname, String descriptor,
                              org.hotswap.agent.javassist.compiler.ast.ASTList args)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        target.accept(this);
        int nargs = getMethodArgsLength(args);
        atMethodArgs(args, new int[nargs], new int[nargs],
                new String[nargs]);
        setReturnType(descriptor);
        addNullIfVoid();
    }

    protected void compileUnwrapValue(org.hotswap.agent.javassist.CtClass type) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (type == org.hotswap.agent.javassist.CtClass.voidType)
            addNullIfVoid();
        else
            setType(type);
    }

    /* Sets exprType, arrayDim, and className;
     * If type is void, then this method does nothing.
     */
    public void setType(org.hotswap.agent.javassist.CtClass type) throws org.hotswap.agent.javassist.compiler.CompileError {
        setType(type, 0);
    }

    private void setType(org.hotswap.agent.javassist.CtClass type, int dim) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (type.isPrimitive()) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) type;
            exprType = org.hotswap.agent.javassist.compiler.MemberResolver.descToType(pt.getDescriptor());
            arrayDim = dim;
            className = null;
        } else if (type.isArray())
            try {
                setType(type.getComponentType(), dim + 1);
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
                throw new org.hotswap.agent.javassist.compiler.CompileError("undefined type: " + type.getName());
            }
        else {
            exprType = CLASS;
            arrayDim = dim;
            className = org.hotswap.agent.javassist.compiler.MemberResolver.javaToJvmName(type.getName());
        }
    }
}
