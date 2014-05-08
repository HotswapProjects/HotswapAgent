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

/* Code generator accepting extended Java syntax for Javassist.
 */

public class JvstCodeGen extends MemberCodeGen {
    String paramArrayName = null;
    String paramListName = null;
    org.hotswap.agent.javassist.CtClass[] paramTypeList = null;
    private int paramVarBase = 0;       // variable index for $0 or $1.
    private boolean useParam0 = false;  // true if $0 is used.
    private String param0Type = null;   // JVM name
    public static final String sigName = "$sig";
    public static final String dollarTypeName = "$type";
    public static final String clazzName = "$class";
    private org.hotswap.agent.javassist.CtClass dollarType = null;
    org.hotswap.agent.javassist.CtClass returnType = null;
    String returnCastName = null;
    private String returnVarName = null;        // null if $_ is not used.
    public static final String wrapperCastName = "$w";
    String proceedName = null;
    public static final String cflowName = "$cflow";
    ProceedHandler procHandler = null;  // null if not used.

    public JvstCodeGen(org.hotswap.agent.javassist.bytecode.Bytecode b, org.hotswap.agent.javassist.CtClass cc, org.hotswap.agent.javassist.ClassPool cp) {
        super(b, cc, cp);
        setTypeChecker(new JvstTypeChecker(cc, cp, this));
    }

    /* Index of $1.
     */
    private int indexOfParam1() {
        return paramVarBase + (useParam0 ? 1 : 0);
    }

    /* Records a ProceedHandler obejct.
     *
     * @param name      the name of the special method call.
     *                  it is usually $proceed.
     */
    public void setProceedHandler(ProceedHandler h, String name) {
        proceedName = name;
        procHandler = h;
    }

    /* If the type of the expression compiled last is void,
     * add ACONST_NULL and change exprType, arrayDim, className.
     */
    public void addNullIfVoid() {
        if (exprType == TokenId.VOID) {
            bytecode.addOpcode(ACONST_NULL);
            exprType = TokenId.CLASS;
            arrayDim = 0;
            className = jvmJavaLangObject;
        }
    }

    /* To support $args, $sig, and $type.
     * $args is an array of parameter list.
     */
    public void atMember(org.hotswap.agent.javassist.compiler.ast.Member mem) throws CompileError {
        String name = mem.get();
        if (name.equals(paramArrayName)) {
            compileParameterList(bytecode, paramTypeList, indexOfParam1());
            exprType = TokenId.CLASS;
            arrayDim = 1;
            className = jvmJavaLangObject;
        } else if (name.equals(sigName)) {
            bytecode.addLdc(org.hotswap.agent.javassist.bytecode.Descriptor.ofMethod(returnType, paramTypeList));
            bytecode.addInvokestatic("javassist/runtime/Desc", "getParams",
                    "(Ljava/lang/String;)[Ljava/lang/Class;");
            exprType = TokenId.CLASS;
            arrayDim = 1;
            className = "java/lang/Class";
        } else if (name.equals(dollarTypeName)) {
            if (dollarType == null)
                throw new CompileError(dollarTypeName + " is not available");

            bytecode.addLdc(org.hotswap.agent.javassist.bytecode.Descriptor.of(dollarType));
            callGetType("getType");
        } else if (name.equals(clazzName)) {
            if (param0Type == null)
                throw new CompileError(clazzName + " is not available");

            bytecode.addLdc(param0Type);
            callGetType("getClazz");
        } else
            super.atMember(mem);
    }

    private void callGetType(String method) {
        bytecode.addInvokestatic("javassist/runtime/Desc", method,
                "(Ljava/lang/String;)Ljava/lang/Class;");
        exprType = TokenId.CLASS;
        arrayDim = 0;
        className = "java/lang/Class";
    }

    protected void atFieldAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree left,
                                 org.hotswap.agent.javassist.compiler.ast.ASTree right, boolean doDup) throws CompileError {
        if (left instanceof org.hotswap.agent.javassist.compiler.ast.Member
                && ((org.hotswap.agent.javassist.compiler.ast.Member) left).get().equals(paramArrayName)) {
            if (op != '=')
                throw new CompileError("bad operator for " + paramArrayName);

            right.accept(this);
            if (arrayDim != 1 || exprType != TokenId.CLASS)
                throw new CompileError("invalid type for " + paramArrayName);

            atAssignParamList(paramTypeList, bytecode);
            if (!doDup)
                bytecode.addOpcode(POP);
        } else
            super.atFieldAssign(expr, op, left, right, doDup);
    }

    protected void atAssignParamList(org.hotswap.agent.javassist.CtClass[] params, org.hotswap.agent.javassist.bytecode.Bytecode code)
            throws CompileError {
        if (params == null)
            return;

        int varNo = indexOfParam1();
        int n = params.length;
        for (int i = 0; i < n; ++i) {
            code.addOpcode(DUP);
            code.addIconst(i);
            code.addOpcode(AALOAD);
            compileUnwrapValue(params[i], code);
            code.addStore(varNo, params[i]);
            varNo += is2word(exprType, arrayDim) ? 2 : 1;
        }
    }

    public void atCastExpr(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList classname = expr.getClassName();
        if (classname != null && expr.getArrayDim() == 0) {
            org.hotswap.agent.javassist.compiler.ast.ASTree p = classname.head();
            if (p instanceof org.hotswap.agent.javassist.compiler.ast.Symbol && classname.tail() == null) {
                String typename = ((org.hotswap.agent.javassist.compiler.ast.Symbol) p).get();
                if (typename.equals(returnCastName)) {
                    atCastToRtype(expr);
                    return;
                } else if (typename.equals(wrapperCastName)) {
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
    protected void atCastToRtype(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws CompileError {
        expr.getOprand().accept(this);
        if (exprType == TokenId.VOID || isRefType(exprType) || arrayDim > 0)
            compileUnwrapValue(returnType, bytecode);
        else if (returnType instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) returnType;
            int destType = MemberResolver.descToType(pt.getDescriptor());
            atNumCastExpr(exprType, destType);
            exprType = destType;
            arrayDim = 0;
            className = null;
        } else
            throw new CompileError("invalid cast");
    }

    protected void atCastToWrapper(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws CompileError {
        expr.getOprand().accept(this);
        if (isRefType(exprType) || arrayDim > 0)
            return;     // Object type.  do nothing.

        org.hotswap.agent.javassist.CtClass clazz = resolver.lookupClass(exprType, arrayDim, className);
        if (clazz instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) clazz;
            String wrapper = pt.getWrapperName();
            bytecode.addNew(wrapper);           // new <wrapper>
            bytecode.addOpcode(DUP);            // dup
            if (pt.getDataSize() > 1)
                bytecode.addOpcode(DUP2_X2);    // dup2_x2
            else
                bytecode.addOpcode(DUP2_X1);    // dup2_x1

            bytecode.addOpcode(POP2);           // pop2
            bytecode.addInvokespecial(wrapper, "<init>",
                    "(" + pt.getDescriptor() + ")V");
            // invokespecial
            exprType = TokenId.CLASS;
            arrayDim = 0;
            className = jvmJavaLangObject;
        }
    }

    /* Delegates to a ProcHandler object if the method call is
     * $proceed().  It may process $cflow().
     */
    public void atCallExpr(org.hotswap.agent.javassist.compiler.ast.CallExpr expr) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree method = expr.oprand1();
        if (method instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            String name = ((org.hotswap.agent.javassist.compiler.ast.Member) method).get();
            if (procHandler != null && name.equals(proceedName)) {
                procHandler.doit(this, bytecode, (org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2());
                return;
            } else if (name.equals(cflowName)) {
                atCflow((org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2());
                return;
            }
        }

        super.atCallExpr(expr);
    }

    /* To support $cflow().
     */
    protected void atCflow(org.hotswap.agent.javassist.compiler.ast.ASTList cname) throws CompileError {
        StringBuffer sbuf = new StringBuffer();
        if (cname == null || cname.tail() != null)
            throw new CompileError("bad " + cflowName);

        makeCflowName(sbuf, cname.head());
        String name = sbuf.toString();
        Object[] names = resolver.getClassPool().lookupCflow(name);
        if (names == null)
            throw new CompileError("no such " + cflowName + ": " + name);

        bytecode.addGetstatic((String) names[0], (String) names[1],
                "Ljavassist/runtime/Cflow;");
        bytecode.addInvokevirtual("Cflow",
                "value", "()I");
        exprType = TokenId.INT;
        arrayDim = 0;
        className = null;
    }

    /* Syntax:
     *
     * <cflow> : $cflow '(' <cflow name> ')'
     * <cflow name> : <identifier> ('.' <identifier>)*
     */
    private static void makeCflowName(StringBuffer sbuf, org.hotswap.agent.javassist.compiler.ast.ASTree name)
            throws CompileError {
        if (name instanceof org.hotswap.agent.javassist.compiler.ast.Symbol) {
            sbuf.append(((org.hotswap.agent.javassist.compiler.ast.Symbol) name).get());
            return;
        } else if (name instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr expr = (org.hotswap.agent.javassist.compiler.ast.Expr) name;
            if (expr.getOperator() == '.') {
                makeCflowName(sbuf, expr.oprand1());
                sbuf.append('.');
                makeCflowName(sbuf, expr.oprand2());
                return;
            }
        }

        throw new CompileError("bad " + cflowName);
    }

    /* To support $$.  ($$) is equivalent to ($1, ..., $n).
     * It can be used only as a parameter list of method call.
     */
    public boolean isParamListName(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        if (paramTypeList != null
                && args != null && args.tail() == null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree left = args.head();
            return (left instanceof org.hotswap.agent.javassist.compiler.ast.Member
                    && ((org.hotswap.agent.javassist.compiler.ast.Member) left).get().equals(paramListName));
        } else
            return false;
    }

    /*
    public int getMethodArgsLength(ASTList args) {
        if (!isParamListName(args))
            return super.getMethodArgsLength(args);

        return paramTypeList.length;
    }
    */

    public int getMethodArgsLength(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        String pname = paramListName;
        int n = 0;
        while (args != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree a = args.head();
            if (a instanceof org.hotswap.agent.javassist.compiler.ast.Member && ((org.hotswap.agent.javassist.compiler.ast.Member) a).get().equals(pname)) {
                if (paramTypeList != null)
                    n += paramTypeList.length;
            } else
                ++n;

            args = args.tail();
        }

        return n;
    }

    public void atMethodArgs(org.hotswap.agent.javassist.compiler.ast.ASTList args, int[] types, int[] dims,
                             String[] cnames) throws CompileError {
        org.hotswap.agent.javassist.CtClass[] params = paramTypeList;
        String pname = paramListName;
        int i = 0;
        while (args != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree a = args.head();
            if (a instanceof org.hotswap.agent.javassist.compiler.ast.Member && ((org.hotswap.agent.javassist.compiler.ast.Member) a).get().equals(pname)) {
                if (params != null) {
                    int n = params.length;
                    int regno = indexOfParam1();
                    for (int k = 0; k < n; ++k) {
                        org.hotswap.agent.javassist.CtClass p = params[k];
                        regno += bytecode.addLoad(regno, p);
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

    /*
    public void atMethodArgs(ASTList args, int[] types, int[] dims,
                                String[] cnames) throws CompileError {
        if (!isParamListName(args)) {
            super.atMethodArgs(args, types, dims, cnames);
            return;
        }

        CtClass[] params = paramTypeList;
        if (params == null)
            return;

        int n = params.length;
        int regno = indexOfParam1();
        for (int i = 0; i < n; ++i) {
            CtClass p = params[i];
            regno += bytecode.addLoad(regno, p);
            setType(p);
            types[i] = exprType;
            dims[i] = arrayDim;
            cnames[i] = className;
        }
    }
    */

    /* called by Javac#recordSpecialProceed().
     */
    void compileInvokeSpecial(org.hotswap.agent.javassist.compiler.ast.ASTree target, String classname,
                              String methodname, String descriptor,
                              org.hotswap.agent.javassist.compiler.ast.ASTList args)
            throws CompileError {
        target.accept(this);
        int nargs = getMethodArgsLength(args);
        atMethodArgs(args, new int[nargs], new int[nargs],
                new String[nargs]);
        bytecode.addInvokespecial(classname, methodname, descriptor);
        setReturnType(descriptor, false, false);
        addNullIfVoid();
    }

    /*
     * Makes it valid to write "return <expr>;" for a void method.
     */
    protected void atReturnStmnt(org.hotswap.agent.javassist.compiler.ast.Stmnt st) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree result = st.getLeft();
        if (result != null && returnType == org.hotswap.agent.javassist.CtClass.voidType) {
            compileExpr(result);
            if (is2word(exprType, arrayDim))
                bytecode.addOpcode(POP2);
            else if (exprType != TokenId.VOID)
                bytecode.addOpcode(POP);

            result = null;
        }

        atReturnStmnt2(result);
    }

    /**
     * Makes a cast to the return type ($r) available.
     * It also enables $_.
     * <p/>
     * <p>If the return type is void, ($r) does nothing.
     * The type of $_ is java.lang.Object.
     *
     * @param resultName null if $_ is not used.
     * @return -1 or the variable index assigned to $_.
     */
    public int recordReturnType(org.hotswap.agent.javassist.CtClass type, String castName,
                                String resultName, SymbolTable tbl) throws CompileError {
        returnType = type;
        returnCastName = castName;
        returnVarName = resultName;
        if (resultName == null)
            return -1;
        else {
            int varNo = getMaxLocals();
            int locals = varNo + recordVar(type, resultName, varNo, tbl);
            setMaxLocals(locals);
            return varNo;
        }
    }

    /**
     * Makes $type available.
     */
    public void recordType(org.hotswap.agent.javassist.CtClass t) {
        dollarType = t;
    }

    /**
     * Makes method parameters $0, $1, ..., $args, $$, and $class available.
     * $0 is equivalent to THIS if the method is not static.  Otherwise,
     * if the method is static, then $0 is not available.
     */
    public int recordParams(org.hotswap.agent.javassist.CtClass[] params, boolean isStatic,
                            String prefix, String paramVarName,
                            String paramsName, SymbolTable tbl)
            throws CompileError {
        return recordParams(params, isStatic, prefix, paramVarName,
                paramsName, !isStatic, 0, getThisName(), tbl);
    }

    /**
     * Makes method parameters $0, $1, ..., $args, $$, and $class available.
     * $0 is available only if use0 is true.  It might not be equivalent
     * to THIS.
     *
     * @param params       the parameter types (the types of $1, $2, ..)
     * @param prefix       it must be "$" (the first letter of $0, $1, ...)
     * @param paramVarName it must be "$args"
     * @param paramsName   it must be "$$"
     * @param use0         true if $0 is used.
     * @param paramBase    the register number of $0 (use0 is true)
     *                     or $1 (otherwise).
     * @param target       the class of $0.  If use0 is false, target
     *                     can be null.  The value of "target" is also used
     *                     as the name of the type represented by $class.
     * @param isStatic     true if the method in which the compiled bytecode
     *                     is embedded is static.
     */
    public int recordParams(org.hotswap.agent.javassist.CtClass[] params, boolean isStatic,
                            String prefix, String paramVarName,
                            String paramsName, boolean use0,
                            int paramBase, String target,
                            SymbolTable tbl)
            throws CompileError {
        int varNo;

        paramTypeList = params;
        paramArrayName = paramVarName;
        paramListName = paramsName;
        paramVarBase = paramBase;
        useParam0 = use0;

        if (target != null)
            param0Type = MemberResolver.jvmToJavaName(target);

        inStaticMethod = isStatic;
        varNo = paramBase;
        if (use0) {
            String varName = prefix + "0";
            org.hotswap.agent.javassist.compiler.ast.Declarator decl
                    = new org.hotswap.agent.javassist.compiler.ast.Declarator(TokenId.CLASS, MemberResolver.javaToJvmName(target),
                    0, varNo++, new org.hotswap.agent.javassist.compiler.ast.Symbol(varName));
            tbl.append(varName, decl);
        }

        for (int i = 0; i < params.length; ++i)
            varNo += recordVar(params[i], prefix + (i + 1), varNo, tbl);

        if (getMaxLocals() < varNo)
            setMaxLocals(varNo);

        return varNo;
    }

    /**
     * Makes the given variable name available.
     *
     * @param type    variable type
     * @param varName variable name
     */
    public int recordVariable(org.hotswap.agent.javassist.CtClass type, String varName, SymbolTable tbl)
            throws CompileError {
        if (varName == null)
            return -1;
        else {
            int varNo = getMaxLocals();
            int locals = varNo + recordVar(type, varName, varNo, tbl);
            setMaxLocals(locals);
            return varNo;
        }
    }

    private int recordVar(org.hotswap.agent.javassist.CtClass cc, String varName, int varNo,
                          SymbolTable tbl) throws CompileError {
        if (cc == org.hotswap.agent.javassist.CtClass.voidType) {
            exprType = TokenId.CLASS;
            arrayDim = 0;
            className = jvmJavaLangObject;
        } else
            setType(cc);

        org.hotswap.agent.javassist.compiler.ast.Declarator decl
                = new org.hotswap.agent.javassist.compiler.ast.Declarator(exprType, className, arrayDim,
                varNo, new org.hotswap.agent.javassist.compiler.ast.Symbol(varName));
        tbl.append(varName, decl);
        return is2word(exprType, arrayDim) ? 2 : 1;
    }

    /**
     * Makes the given variable name available.
     *
     * @param typeDesc the type descriptor of the variable
     * @param varName  variable name
     * @param varNo    an index into the local variable array
     */
    public void recordVariable(String typeDesc, String varName, int varNo,
                               SymbolTable tbl) throws CompileError {
        char c;
        int dim = 0;
        while ((c = typeDesc.charAt(dim)) == '[')
            ++dim;

        int type = MemberResolver.descToType(c);
        String cname = null;
        if (type == TokenId.CLASS) {
            if (dim == 0)
                cname = typeDesc.substring(1, typeDesc.length() - 1);
            else
                cname = typeDesc.substring(dim + 1, typeDesc.length() - 1);
        }

        org.hotswap.agent.javassist.compiler.ast.Declarator decl
                = new org.hotswap.agent.javassist.compiler.ast.Declarator(type, cname, dim, varNo, new org.hotswap.agent.javassist.compiler.ast.Symbol(varName));
        tbl.append(varName, decl);
    }

    /* compileParameterList() returns the stack size used
     * by the produced code.
     *
     * This method correctly computes the max_stack value.
     *
     * @param regno     the index of the local variable in which
     *                  the first argument is received.
     *                  (0: static method, 1: regular method.)
     */
    public static int compileParameterList(org.hotswap.agent.javassist.bytecode.Bytecode code,
                                           org.hotswap.agent.javassist.CtClass[] params, int regno) {
        if (params == null) {
            code.addIconst(0);                          // iconst_0
            code.addAnewarray(javaLangObject);          // anewarray Object
            return 1;
        } else {
            org.hotswap.agent.javassist.CtClass[] args = new org.hotswap.agent.javassist.CtClass[1];
            int n = params.length;
            code.addIconst(n);                          // iconst_<n>
            code.addAnewarray(javaLangObject);          // anewarray Object
            for (int i = 0; i < n; ++i) {
                code.addOpcode(org.hotswap.agent.javassist.bytecode.Bytecode.DUP);           // dup
                code.addIconst(i);                      // iconst_<i>
                if (params[i].isPrimitive()) {
                    org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) params[i];
                    String wrapper = pt.getWrapperName();
                    code.addNew(wrapper);               // new <wrapper>
                    code.addOpcode(org.hotswap.agent.javassist.bytecode.Bytecode.DUP);       // dup
                    int s = code.addLoad(regno, pt);    // ?load <regno>
                    regno += s;
                    args[0] = pt;
                    code.addInvokespecial(wrapper, "<init>",
                            org.hotswap.agent.javassist.bytecode.Descriptor.ofMethod(org.hotswap.agent.javassist.CtClass.voidType, args));
                    // invokespecial
                } else {
                    code.addAload(regno);               // aload <regno>
                    ++regno;
                }

                code.addOpcode(org.hotswap.agent.javassist.bytecode.Bytecode.AASTORE);       // aastore
            }

            return 8;
        }
    }

    protected void compileUnwrapValue(org.hotswap.agent.javassist.CtClass type, org.hotswap.agent.javassist.bytecode.Bytecode code)
            throws CompileError {
        if (type == org.hotswap.agent.javassist.CtClass.voidType) {
            addNullIfVoid();
            return;
        }

        if (exprType == TokenId.VOID)
            throw new CompileError("invalid type for " + returnCastName);

        if (type instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) type;
            // pt is not voidType.
            String wrapper = pt.getWrapperName();
            code.addCheckcast(wrapper);
            code.addInvokevirtual(wrapper, pt.getGetMethodName(),
                    pt.getGetMethodDescriptor());
            setType(type);
        } else {
            code.addCheckcast(type);
            setType(type);
        }
    }

    /* Sets exprType, arrayDim, and className;
     * If type is void, then this method does nothing.
     */
    public void setType(org.hotswap.agent.javassist.CtClass type) throws CompileError {
        setType(type, 0);
    }

    private void setType(org.hotswap.agent.javassist.CtClass type, int dim) throws CompileError {
        if (type.isPrimitive()) {
            org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) type;
            exprType = MemberResolver.descToType(pt.getDescriptor());
            arrayDim = dim;
            className = null;
        } else if (type.isArray())
            try {
                setType(type.getComponentType(), dim + 1);
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
                throw new CompileError("undefined type: " + type.getName());
            }
        else {
            exprType = TokenId.CLASS;
            arrayDim = dim;
            className = MemberResolver.javaToJvmName(type.getName());
        }
    }

    /* Performs implicit coercion from exprType to type.
     */
    public void doNumCast(org.hotswap.agent.javassist.CtClass type) throws CompileError {
        if (arrayDim == 0 && !isRefType(exprType))
            if (type instanceof org.hotswap.agent.javassist.CtPrimitiveType) {
                org.hotswap.agent.javassist.CtPrimitiveType pt = (org.hotswap.agent.javassist.CtPrimitiveType) type;
                atNumCastExpr(exprType,
                        MemberResolver.descToType(pt.getDescriptor()));
            } else
                throw new CompileError("type mismatch");
    }
}
