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

import org.hotswap.agent.javassist.compiler.TokenId;

import java.util.ArrayList;

/* Code generator methods depending on javassist.* classes.
 */
public class MemberCodeGen extends CodeGen {
    protected org.hotswap.agent.javassist.compiler.MemberResolver resolver;
    protected org.hotswap.agent.javassist.CtClass thisClass;
    protected org.hotswap.agent.javassist.bytecode.MethodInfo thisMethod;

    protected boolean resultStatic;

    public MemberCodeGen(org.hotswap.agent.javassist.bytecode.Bytecode b, org.hotswap.agent.javassist.CtClass cc, org.hotswap.agent.javassist.ClassPool cp) {
        super(b);
        resolver = new org.hotswap.agent.javassist.compiler.MemberResolver(cp);
        thisClass = cc;
        thisMethod = null;
    }

    /**
     * Returns the major version of the class file
     * targeted by this compilation.
     */
    public int getMajorVersion() {
        org.hotswap.agent.javassist.bytecode.ClassFile cf = thisClass.getClassFile2();
        if (cf == null)
            return org.hotswap.agent.javassist.bytecode.ClassFile.MAJOR_VERSION;     // JDK 1.3
        else
            return cf.getMajorVersion();
    }

    /**
     * Records the currently compiled method.
     */
    public void setThisMethod(org.hotswap.agent.javassist.CtMethod m) {
        thisMethod = m.getMethodInfo2();
        if (typeChecker != null)
            typeChecker.setThisMethod(thisMethod);
    }

    public org.hotswap.agent.javassist.CtClass getThisClass() {
        return thisClass;
    }

    /**
     * Returns the JVM-internal representation of this class name.
     */
    protected String getThisName() {
        return org.hotswap.agent.javassist.compiler.MemberResolver.javaToJvmName(thisClass.getName());
    }

    /**
     * Returns the JVM-internal representation of this super class name.
     */
    protected String getSuperName() throws org.hotswap.agent.javassist.compiler.CompileError {
        return org.hotswap.agent.javassist.compiler.MemberResolver.javaToJvmName(
                org.hotswap.agent.javassist.compiler.MemberResolver.getSuperclass(thisClass).getName());
    }

    protected void insertDefaultSuperCall() throws org.hotswap.agent.javassist.compiler.CompileError {
        bytecode.addAload(0);
        bytecode.addInvokespecial(org.hotswap.agent.javassist.compiler.MemberResolver.getSuperclass(thisClass),
                "<init>", "()V");
    }

    static class JsrHook extends ReturnHook {
        ArrayList jsrList;
        CodeGen cgen;
        int var;

        JsrHook(CodeGen gen) {
            super(gen);
            jsrList = new ArrayList();
            cgen = gen;
            var = -1;
        }

        private int getVar(int size) {
            if (var < 0) {
                var = cgen.getMaxLocals();
                cgen.incMaxLocals(size);
            }

            return var;
        }

        private void jsrJmp(org.hotswap.agent.javassist.bytecode.Bytecode b) {
            b.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.GOTO);
            jsrList.add(new int[]{b.currentPc(), var});
            b.addIndex(0);
        }

        protected boolean doit(org.hotswap.agent.javassist.bytecode.Bytecode b, int opcode) {
            switch (opcode) {
                case org.hotswap.agent.javassist.bytecode.Opcode.RETURN:
                    jsrJmp(b);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.ARETURN:
                    b.addAstore(getVar(1));
                    jsrJmp(b);
                    b.addAload(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.IRETURN:
                    b.addIstore(getVar(1));
                    jsrJmp(b);
                    b.addIload(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.LRETURN:
                    b.addLstore(getVar(2));
                    jsrJmp(b);
                    b.addLload(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.DRETURN:
                    b.addDstore(getVar(2));
                    jsrJmp(b);
                    b.addDload(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.FRETURN:
                    b.addFstore(getVar(1));
                    jsrJmp(b);
                    b.addFload(var);
                    break;
                default:
                    throw new RuntimeException("fatal");
            }

            return false;
        }
    }

    static class JsrHook2 extends ReturnHook {
        int var;
        int target;

        JsrHook2(CodeGen gen, int[] retTarget) {
            super(gen);
            target = retTarget[0];
            var = retTarget[1];
        }

        protected boolean doit(org.hotswap.agent.javassist.bytecode.Bytecode b, int opcode) {
            switch (opcode) {
                case org.hotswap.agent.javassist.bytecode.Opcode.RETURN:
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.ARETURN:
                    b.addAstore(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.IRETURN:
                    b.addIstore(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.LRETURN:
                    b.addLstore(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.DRETURN:
                    b.addDstore(var);
                    break;
                case org.hotswap.agent.javassist.bytecode.Opcode.FRETURN:
                    b.addFstore(var);
                    break;
                default:
                    throw new RuntimeException("fatal");
            }

            b.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.GOTO);
            b.addIndex(target - b.currentPc() + 3);
            return true;
        }
    }

    protected void atTryStmnt(org.hotswap.agent.javassist.compiler.ast.Stmnt st) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.bytecode.Bytecode bc = bytecode;
        org.hotswap.agent.javassist.compiler.ast.Stmnt body = (org.hotswap.agent.javassist.compiler.ast.Stmnt) st.getLeft();
        if (body == null)
            return;

        org.hotswap.agent.javassist.compiler.ast.ASTList catchList = (org.hotswap.agent.javassist.compiler.ast.ASTList) st.getRight().getLeft();
        org.hotswap.agent.javassist.compiler.ast.Stmnt finallyBlock = (org.hotswap.agent.javassist.compiler.ast.Stmnt) st.getRight().getRight().getLeft();
        ArrayList gotoList = new ArrayList();

        JsrHook jsrHook = null;
        if (finallyBlock != null)
            jsrHook = new JsrHook(this);

        int start = bc.currentPc();
        body.accept(this);
        int end = bc.currentPc();
        if (start == end)
            throw new org.hotswap.agent.javassist.compiler.CompileError("empty try block");

        boolean tryNotReturn = !hasReturned;
        if (tryNotReturn) {
            bc.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.GOTO);
            gotoList.add(new Integer(bc.currentPc()));
            bc.addIndex(0);   // correct later
        }

        int var = getMaxLocals();
        incMaxLocals(1);
        while (catchList != null) {
            // catch clause
            org.hotswap.agent.javassist.compiler.ast.Pair p = (org.hotswap.agent.javassist.compiler.ast.Pair) catchList.head();
            catchList = catchList.tail();
            org.hotswap.agent.javassist.compiler.ast.Declarator decl = (org.hotswap.agent.javassist.compiler.ast.Declarator) p.getLeft();
            org.hotswap.agent.javassist.compiler.ast.Stmnt block = (org.hotswap.agent.javassist.compiler.ast.Stmnt) p.getRight();

            decl.setLocalVar(var);

            org.hotswap.agent.javassist.CtClass type = resolver.lookupClassByJvmName(decl.getClassName());
            decl.setClassName(org.hotswap.agent.javassist.compiler.MemberResolver.javaToJvmName(type.getName()));
            bc.addExceptionHandler(start, end, bc.currentPc(), type);
            bc.growStack(1);
            bc.addAstore(var);
            hasReturned = false;
            if (block != null)
                block.accept(this);

            if (!hasReturned) {
                bc.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.GOTO);
                gotoList.add(new Integer(bc.currentPc()));
                bc.addIndex(0);   // correct later
                tryNotReturn = true;
            }
        }

        if (finallyBlock != null) {
            jsrHook.remove(this);
            // catch (any) clause
            int pcAnyCatch = bc.currentPc();
            bc.addExceptionHandler(start, pcAnyCatch, pcAnyCatch, 0);
            bc.growStack(1);
            bc.addAstore(var);
            hasReturned = false;
            finallyBlock.accept(this);
            if (!hasReturned) {
                bc.addAload(var);
                bc.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.ATHROW);
            }

            addFinally(jsrHook.jsrList, finallyBlock);
        }

        int pcEnd = bc.currentPc();
        patchGoto(gotoList, pcEnd);
        hasReturned = !tryNotReturn;
        if (finallyBlock != null) {
            if (tryNotReturn)
                finallyBlock.accept(this);
        }
    }

    /**
     * Adds a finally clause for earch return statement.
     */
    private void addFinally(ArrayList returnList, org.hotswap.agent.javassist.compiler.ast.Stmnt finallyBlock)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.bytecode.Bytecode bc = bytecode;
        int n = returnList.size();
        for (int i = 0; i < n; ++i) {
            final int[] ret = (int[]) returnList.get(i);
            int pc = ret[0];
            bc.write16bit(pc, bc.currentPc() - pc + 1);
            ReturnHook hook = new JsrHook2(this, ret);
            finallyBlock.accept(this);
            hook.remove(this);
            if (!hasReturned) {
                bc.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.GOTO);
                bc.addIndex(pc + 3 - bc.currentPc());
            }
        }
    }

    public void atNewExpr(org.hotswap.agent.javassist.compiler.ast.NewExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (expr.isArray())
            atNewArrayExpr(expr);
        else {
            org.hotswap.agent.javassist.CtClass clazz = resolver.lookupClassByName(expr.getClassName());
            String cname = clazz.getName();
            org.hotswap.agent.javassist.compiler.ast.ASTList args = expr.getArguments();
            bytecode.addNew(cname);
            bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.DUP);

            atMethodCallCore(clazz, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, args,
                    false, true, -1, null);

            exprType = org.hotswap.agent.javassist.compiler.TokenId.CLASS;
            arrayDim = 0;
            className = org.hotswap.agent.javassist.compiler.MemberResolver.javaToJvmName(cname);
        }
    }

    public void atNewArrayExpr(org.hotswap.agent.javassist.compiler.ast.NewExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        int type = expr.getArrayType();
        org.hotswap.agent.javassist.compiler.ast.ASTList size = expr.getArraySize();
        org.hotswap.agent.javassist.compiler.ast.ASTList classname = expr.getClassName();
        org.hotswap.agent.javassist.compiler.ast.ArrayInit init = expr.getInitializer();
        if (size.length() > 1) {
            if (init != null)
                throw new org.hotswap.agent.javassist.compiler.CompileError(
                        "sorry, multi-dimensional array initializer " +
                                "for new is not supported");

            atMultiNewArray(type, classname, size);
            return;
        }

        org.hotswap.agent.javassist.compiler.ast.ASTree sizeExpr = size.head();
        atNewArrayExpr2(type, sizeExpr, org.hotswap.agent.javassist.compiler.ast.Declarator.astToClassName(classname, '/'), init);
    }

    private void atNewArrayExpr2(int type, org.hotswap.agent.javassist.compiler.ast.ASTree sizeExpr,
                                 String jvmClassname, org.hotswap.agent.javassist.compiler.ast.ArrayInit init) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (init == null)
            if (sizeExpr == null)
                throw new org.hotswap.agent.javassist.compiler.CompileError("no array size");
            else
                sizeExpr.accept(this);
        else if (sizeExpr == null) {
            int s = init.length();
            bytecode.addIconst(s);
        } else
            throw new org.hotswap.agent.javassist.compiler.CompileError("unnecessary array size specified for new");

        String elementClass;
        if (type == TokenId.CLASS) {
            elementClass = resolveClassName(jvmClassname);
            bytecode.addAnewarray(org.hotswap.agent.javassist.compiler.MemberResolver.jvmToJavaName(elementClass));
        } else {
            elementClass = null;
            int atype = 0;
            switch (type) {
                case TokenId.BOOLEAN:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_BOOLEAN;
                    break;
                case TokenId.CHAR:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_CHAR;
                    break;
                case TokenId.FLOAT:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_FLOAT;
                    break;
                case TokenId.DOUBLE:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_DOUBLE;
                    break;
                case TokenId.BYTE:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_BYTE;
                    break;
                case TokenId.SHORT:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_SHORT;
                    break;
                case TokenId.INT:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_INT;
                    break;
                case TokenId.LONG:
                    atype = org.hotswap.agent.javassist.bytecode.Opcode.T_LONG;
                    break;
                default:
                    badNewExpr();
                    break;
            }

            bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.NEWARRAY);
            bytecode.add(atype);
        }

        if (init != null) {
            int s = init.length();
            org.hotswap.agent.javassist.compiler.ast.ASTList list = init;
            for (int i = 0; i < s; i++) {
                bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.DUP);
                bytecode.addIconst(i);
                list.head().accept(this);
                if (!isRefType(type))
                    atNumCastExpr(exprType, type);

                bytecode.addOpcode(getArrayWriteOp(type, 0));
                list = list.tail();
            }
        }

        exprType = type;
        arrayDim = 1;
        className = elementClass;
    }

    private static void badNewExpr() throws org.hotswap.agent.javassist.compiler.CompileError {
        throw new org.hotswap.agent.javassist.compiler.CompileError("bad new expression");
    }

    protected void atArrayVariableAssign(org.hotswap.agent.javassist.compiler.ast.ArrayInit init, int varType,
                                         int varArray, String varClass) throws org.hotswap.agent.javassist.compiler.CompileError {
        atNewArrayExpr2(varType, null, varClass, init);
    }

    public void atArrayInit(org.hotswap.agent.javassist.compiler.ast.ArrayInit init) throws org.hotswap.agent.javassist.compiler.CompileError {
        throw new org.hotswap.agent.javassist.compiler.CompileError("array initializer is not supported");
    }

    protected void atMultiNewArray(int type, org.hotswap.agent.javassist.compiler.ast.ASTList classname, org.hotswap.agent.javassist.compiler.ast.ASTList size)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        int count, dim;
        dim = size.length();
        for (count = 0; size != null; size = size.tail()) {
            org.hotswap.agent.javassist.compiler.ast.ASTree s = size.head();
            if (s == null)
                break;          // int[][][] a = new int[3][4][];

            ++count;
            s.accept(this);
            if (exprType != TokenId.INT)
                throw new org.hotswap.agent.javassist.compiler.CompileError("bad type for array size");
        }

        String desc;
        exprType = type;
        arrayDim = dim;
        if (type == TokenId.CLASS) {
            className = resolveClassName(classname);
            desc = toJvmArrayName(className, dim);
        } else
            desc = toJvmTypeName(type, dim);

        bytecode.addMultiNewarray(desc, count);
    }

    public void atCallExpr(org.hotswap.agent.javassist.compiler.ast.CallExpr expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        String mname = null;
        org.hotswap.agent.javassist.CtClass targetClass = null;
        org.hotswap.agent.javassist.compiler.ast.ASTree method = expr.oprand1();
        org.hotswap.agent.javassist.compiler.ast.ASTList args = (org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2();
        boolean isStatic = false;
        boolean isSpecial = false;
        int aload0pos = -1;

        org.hotswap.agent.javassist.compiler.MemberResolver.Method cached = expr.getMethod();
        if (method instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            mname = ((org.hotswap.agent.javassist.compiler.ast.Member) method).get();
            targetClass = thisClass;
            if (inStaticMethod || (cached != null && cached.isStatic()))
                isStatic = true;            // should be static
            else {
                aload0pos = bytecode.currentPc();
                bytecode.addAload(0);       // this
            }
        } else if (method instanceof org.hotswap.agent.javassist.compiler.ast.Keyword) {   // constructor
            isSpecial = true;
            mname = org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit;        // <init>
            targetClass = thisClass;
            if (inStaticMethod)
                throw new org.hotswap.agent.javassist.compiler.CompileError("a constructor cannot be static");
            else
                bytecode.addAload(0);   // this

            if (((org.hotswap.agent.javassist.compiler.ast.Keyword) method).get() == TokenId.SUPER)
                targetClass = org.hotswap.agent.javassist.compiler.MemberResolver.getSuperclass(targetClass);
        } else if (method instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) method;
            mname = ((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2()).get();
            int op = e.getOperator();
            if (op == TokenId.MEMBER) {                 // static method
                targetClass
                        = resolver.lookupClass(((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand1()).get(), false);
                isStatic = true;
            } else if (op == '.') {
                org.hotswap.agent.javassist.compiler.ast.ASTree target = e.oprand1();
                if (target instanceof org.hotswap.agent.javassist.compiler.ast.Keyword)
                    if (((org.hotswap.agent.javassist.compiler.ast.Keyword) target).get() == TokenId.SUPER)
                        isSpecial = true;

                try {
                    target.accept(this);
                } catch (org.hotswap.agent.javassist.compiler.NoFieldException nfe) {
                    if (nfe.getExpr() != target)
                        throw nfe;

                    // it should be a static method.
                    exprType = TokenId.CLASS;
                    arrayDim = 0;
                    className = nfe.getField(); // JVM-internal
                    isStatic = true;
                }

                if (arrayDim > 0)
                    targetClass = resolver.lookupClass(javaLangObject, true);
                else if (exprType == TokenId.CLASS /* && arrayDim == 0 */)
                    targetClass = resolver.lookupClassByJvmName(className);
                else
                    badMethod();
            } else
                badMethod();
        } else
            fatal();

        atMethodCallCore(targetClass, mname, args, isStatic, isSpecial,
                aload0pos, cached);
    }

    private static void badMethod() throws org.hotswap.agent.javassist.compiler.CompileError {
        throw new org.hotswap.agent.javassist.compiler.CompileError("bad method");
    }

    /*
     * atMethodCallCore() is also called by doit() in NewExpr.ProceedForNew
     *
     * @param targetClass       the class at which method lookup starts.
     * @param found         not null if the method look has been already done.
     */
    public void atMethodCallCore(org.hotswap.agent.javassist.CtClass targetClass, String mname,
                                 org.hotswap.agent.javassist.compiler.ast.ASTList args, boolean isStatic, boolean isSpecial,
                                 int aload0pos, org.hotswap.agent.javassist.compiler.MemberResolver.Method found)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        int nargs = getMethodArgsLength(args);
        int[] types = new int[nargs];
        int[] dims = new int[nargs];
        String[] cnames = new String[nargs];

        if (!isStatic && found != null && found.isStatic()) {
            bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP);
            isStatic = true;
        }

        int stack = bytecode.getStackDepth();

        // generate code for evaluating arguments.
        atMethodArgs(args, types, dims, cnames);

        // used by invokeinterface
        int count = bytecode.getStackDepth() - stack + 1;

        if (found == null)
            found = resolver.lookupMethod(targetClass, thisClass, thisMethod,
                    mname, types, dims, cnames);

        if (found == null) {
            String msg;
            if (mname.equals(org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit))
                msg = "constructor not found";
            else
                msg = "Method " + mname + " not found in "
                        + targetClass.getName();

            throw new org.hotswap.agent.javassist.compiler.CompileError(msg);
        }

        atMethodCallCore2(targetClass, mname, isStatic, isSpecial,
                aload0pos, count, found);
    }

    private void atMethodCallCore2(org.hotswap.agent.javassist.CtClass targetClass, String mname,
                                   boolean isStatic, boolean isSpecial,
                                   int aload0pos, int count,
                                   org.hotswap.agent.javassist.compiler.MemberResolver.Method found)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtClass declClass = found.declaring;
        org.hotswap.agent.javassist.bytecode.MethodInfo minfo = found.info;
        String desc = minfo.getDescriptor();
        int acc = minfo.getAccessFlags();

        if (mname.equals(org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit)) {
            isSpecial = true;
            if (declClass != targetClass)
                throw new org.hotswap.agent.javassist.compiler.CompileError("no such constructor: " + targetClass.getName());

            if (declClass != thisClass && org.hotswap.agent.javassist.bytecode.AccessFlag.isPrivate(acc)) {
                desc = getAccessibleConstructor(desc, declClass, minfo);
                bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.ACONST_NULL); // the last parameter
            }
        } else if (org.hotswap.agent.javassist.bytecode.AccessFlag.isPrivate(acc))
            if (declClass == thisClass)
                isSpecial = true;
            else {
                isSpecial = false;
                isStatic = true;
                String origDesc = desc;
                if ((acc & org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC) == 0)
                    desc = org.hotswap.agent.javassist.bytecode.Descriptor.insertParameter(declClass.getName(),
                            origDesc);

                acc = org.hotswap.agent.javassist.bytecode.AccessFlag.setPackage(acc) | org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC;
                mname = getAccessiblePrivate(mname, origDesc, desc,
                        minfo, declClass);
            }

        boolean popTarget = false;
        if ((acc & org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC) != 0) {
            if (!isStatic) {
                /* this method is static but the target object is
                   on stack.  It must be popped out.  If aload0pos >= 0,
                   then the target object was pushed by aload_0.  It is
                   overwritten by NOP.
                */
                isStatic = true;
                if (aload0pos >= 0)
                    bytecode.write(aload0pos, org.hotswap.agent.javassist.bytecode.Opcode.NOP);
                else
                    popTarget = true;
            }

            bytecode.addInvokestatic(declClass, mname, desc);
        } else if (isSpecial)    // if (isSpecial && notStatic(acc))
            bytecode.addInvokespecial(declClass, mname, desc);
        else {
            if (!org.hotswap.agent.javassist.Modifier.isPublic(declClass.getModifiers())
                    || declClass.isInterface() != targetClass.isInterface())
                declClass = targetClass;

            if (declClass.isInterface())
                bytecode.addInvokeinterface(declClass, mname, desc, count);
            else if (isStatic)
                throw new org.hotswap.agent.javassist.compiler.CompileError(mname + " is not static");
            else
                bytecode.addInvokevirtual(declClass, mname, desc);
        }

        setReturnType(desc, isStatic, popTarget);
    }

    /*
     * Finds (or adds if necessary) a hidden accessor if the method
     * is in an enclosing class.
     *
     * @param desc          the descriptor of the method.
     * @param declClass     the class declaring the method.
     */
    protected String getAccessiblePrivate(String methodName, String desc,
                                          String newDesc, org.hotswap.agent.javassist.bytecode.MethodInfo minfo,
                                          org.hotswap.agent.javassist.CtClass declClass)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        if (isEnclosing(declClass, thisClass)) {
            AccessorMaker maker = declClass.getAccessorMaker();
            if (maker != null)
                return maker.getMethodAccessor(methodName, desc, newDesc,
                        minfo);
        }

        throw new org.hotswap.agent.javassist.compiler.CompileError("Method " + methodName
                + " is private");
    }

    /*
     * Finds (or adds if necessary) a hidden constructor if the given
     * constructor is in an enclosing class.
     *
     * @param desc          the descriptor of the constructor.
     * @param declClass     the class declaring the constructor.
     * @param minfo         the method info of the constructor.
     * @return the descriptor of the hidden constructor.
     */
    protected String getAccessibleConstructor(String desc, org.hotswap.agent.javassist.CtClass declClass,
                                              org.hotswap.agent.javassist.bytecode.MethodInfo minfo)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        if (isEnclosing(declClass, thisClass)) {
            AccessorMaker maker = declClass.getAccessorMaker();
            if (maker != null)
                return maker.getConstructor(declClass, desc, minfo);
        }

        throw new org.hotswap.agent.javassist.compiler.CompileError("the called constructor is private in "
                + declClass.getName());
    }

    private boolean isEnclosing(org.hotswap.agent.javassist.CtClass outer, org.hotswap.agent.javassist.CtClass inner) {
        try {
            while (inner != null) {
                inner = inner.getDeclaringClass();
                if (inner == outer)
                    return true;
            }
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
        }
        return false;
    }

    public int getMethodArgsLength(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        return org.hotswap.agent.javassist.compiler.ast.ASTList.length(args);
    }

    public void atMethodArgs(org.hotswap.agent.javassist.compiler.ast.ASTList args, int[] types, int[] dims,
                             String[] cnames) throws org.hotswap.agent.javassist.compiler.CompileError {
        int i = 0;
        while (args != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree a = args.head();
            a.accept(this);
            types[i] = exprType;
            dims[i] = arrayDim;
            cnames[i] = className;
            ++i;
            args = args.tail();
        }
    }

    void setReturnType(String desc, boolean isStatic, boolean popTarget)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        int i = desc.indexOf(')');
        if (i < 0)
            badMethod();

        char c = desc.charAt(++i);
        int dim = 0;
        while (c == '[') {
            ++dim;
            c = desc.charAt(++i);
        }

        arrayDim = dim;
        if (c == 'L') {
            int j = desc.indexOf(';', i + 1);
            if (j < 0)
                badMethod();

            exprType = TokenId.CLASS;
            className = desc.substring(i + 1, j);
        } else {
            exprType = org.hotswap.agent.javassist.compiler.MemberResolver.descToType(c);
            className = null;
        }

        int etype = exprType;
        if (isStatic) {
            if (popTarget) {
                if (is2word(etype, dim)) {
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.DUP2_X1);
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP2);
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP);
                } else if (etype == TokenId.VOID)
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP);
                else {
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.SWAP);
                    bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP);
                }
            }
        }
    }

    protected void atFieldAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree left,
                                 org.hotswap.agent.javassist.compiler.ast.ASTree right, boolean doDup) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtField f = fieldAccess(left, false);
        boolean is_static = resultStatic;
        if (op != '=' && !is_static)
            bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.DUP);

        int fi;
        if (op == '=') {
            org.hotswap.agent.javassist.bytecode.FieldInfo finfo = f.getFieldInfo2();
            setFieldType(finfo);
            AccessorMaker maker = isAccessibleField(f, finfo);
            if (maker == null)
                fi = addFieldrefInfo(f, finfo);
            else
                fi = 0;
        } else
            fi = atFieldRead(f, is_static);

        int fType = exprType;
        int fDim = arrayDim;
        String cname = className;

        atAssignCore(expr, op, right, fType, fDim, cname);

        boolean is2w = is2word(fType, fDim);
        if (doDup) {
            int dup_code;
            if (is_static)
                dup_code = (is2w ? org.hotswap.agent.javassist.bytecode.Opcode.DUP2 : org.hotswap.agent.javassist.bytecode.Opcode.DUP);
            else
                dup_code = (is2w ? org.hotswap.agent.javassist.bytecode.Opcode.DUP2_X1 : org.hotswap.agent.javassist.bytecode.Opcode.DUP_X1);

            bytecode.addOpcode(dup_code);
        }

        atFieldAssignCore(f, is_static, fi, is2w);

        exprType = fType;
        arrayDim = fDim;
        className = cname;
    }

    /* If fi == 0, the field must be a private field in an enclosing class.
     */
    private void atFieldAssignCore(org.hotswap.agent.javassist.CtField f, boolean is_static, int fi,
                                   boolean is2byte) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (fi != 0) {
            if (is_static) {
                bytecode.add(org.hotswap.agent.javassist.bytecode.Opcode.PUTSTATIC);
                bytecode.growStack(is2byte ? -2 : -1);
            } else {
                bytecode.add(org.hotswap.agent.javassist.bytecode.Opcode.PUTFIELD);
                bytecode.growStack(is2byte ? -3 : -2);
            }

            bytecode.addIndex(fi);
        } else {
            org.hotswap.agent.javassist.CtClass declClass = f.getDeclaringClass();
            AccessorMaker maker = declClass.getAccessorMaker();
            // make should be non null.
            org.hotswap.agent.javassist.bytecode.FieldInfo finfo = f.getFieldInfo2();
            org.hotswap.agent.javassist.bytecode.MethodInfo minfo = maker.getFieldSetter(finfo, is_static);
            bytecode.addInvokestatic(declClass, minfo.getName(),
                    minfo.getDescriptor());
        }
    }

    /* overwritten in JvstCodeGen.
     */
    public void atMember(org.hotswap.agent.javassist.compiler.ast.Member mem) throws org.hotswap.agent.javassist.compiler.CompileError {
        atFieldRead(mem);
    }

    protected void atFieldRead(org.hotswap.agent.javassist.compiler.ast.ASTree expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtField f = fieldAccess(expr, true);
        if (f == null) {
            atArrayLength(expr);
            return;
        }

        boolean is_static = resultStatic;
        org.hotswap.agent.javassist.compiler.ast.ASTree cexpr = org.hotswap.agent.javassist.compiler.TypeChecker.getConstantFieldValue(f);
        if (cexpr == null)
            atFieldRead(f, is_static);
        else {
            cexpr.accept(this);
            setFieldType(f.getFieldInfo2());
        }
    }

    private void atArrayLength(org.hotswap.agent.javassist.compiler.ast.ASTree expr) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (arrayDim == 0)
            throw new org.hotswap.agent.javassist.compiler.CompileError(".length applied to a non array");

        bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.ARRAYLENGTH);
        exprType = TokenId.INT;
        arrayDim = 0;
    }

    /**
     * Generates bytecode for reading a field value.
     * It returns a fieldref_info index or zero if the field is a private
     * one declared in an enclosing class.
     */
    private int atFieldRead(org.hotswap.agent.javassist.CtField f, boolean isStatic) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.bytecode.FieldInfo finfo = f.getFieldInfo2();
        boolean is2byte = setFieldType(finfo);
        AccessorMaker maker = isAccessibleField(f, finfo);
        if (maker != null) {
            org.hotswap.agent.javassist.bytecode.MethodInfo minfo = maker.getFieldGetter(finfo, isStatic);
            bytecode.addInvokestatic(f.getDeclaringClass(), minfo.getName(),
                    minfo.getDescriptor());
            return 0;
        } else {
            int fi = addFieldrefInfo(f, finfo);
            if (isStatic) {
                bytecode.add(org.hotswap.agent.javassist.bytecode.Opcode.GETSTATIC);
                bytecode.growStack(is2byte ? 2 : 1);
            } else {
                bytecode.add(org.hotswap.agent.javassist.bytecode.Opcode.GETFIELD);
                bytecode.growStack(is2byte ? 1 : 0);
            }

            bytecode.addIndex(fi);
            return fi;
        }
    }

    /**
     * Returns null if the field is accessible.  Otherwise, it throws
     * an exception or it returns AccessorMaker if the field is a private
     * one declared in an enclosing class.
     */
    private AccessorMaker isAccessibleField(org.hotswap.agent.javassist.CtField f, org.hotswap.agent.javassist.bytecode.FieldInfo finfo)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        if (org.hotswap.agent.javassist.bytecode.AccessFlag.isPrivate(finfo.getAccessFlags())
                && f.getDeclaringClass() != thisClass) {
            org.hotswap.agent.javassist.CtClass declClass = f.getDeclaringClass();
            if (isEnclosing(declClass, thisClass)) {
                AccessorMaker maker = declClass.getAccessorMaker();
                if (maker != null)
                    return maker;
                else
                    throw new org.hotswap.agent.javassist.compiler.CompileError("fatal error.  bug?");
            } else
                throw new org.hotswap.agent.javassist.compiler.CompileError("Field " + f.getName() + " in "
                        + declClass.getName() + " is private.");
        }

        return null;    // accessible field
    }

    /**
     * Sets exprType, arrayDim, and className.
     *
     * @return true if the field type is long or double.
     */
    private boolean setFieldType(org.hotswap.agent.javassist.bytecode.FieldInfo finfo) throws org.hotswap.agent.javassist.compiler.CompileError {
        String type = finfo.getDescriptor();

        int i = 0;
        int dim = 0;
        char c = type.charAt(i);
        while (c == '[') {
            ++dim;
            c = type.charAt(++i);
        }

        arrayDim = dim;
        exprType = org.hotswap.agent.javassist.compiler.MemberResolver.descToType(c);

        if (c == 'L')
            className = type.substring(i + 1, type.indexOf(';', i + 1));
        else
            className = null;

        boolean is2byte = (c == 'J' || c == 'D');
        return is2byte;
    }

    private int addFieldrefInfo(org.hotswap.agent.javassist.CtField f, org.hotswap.agent.javassist.bytecode.FieldInfo finfo) {
        org.hotswap.agent.javassist.bytecode.ConstPool cp = bytecode.getConstPool();
        String cname = f.getDeclaringClass().getName();
        int ci = cp.addClassInfo(cname);
        String name = finfo.getName();
        String type = finfo.getDescriptor();
        return cp.addFieldrefInfo(ci, name, type);
    }

    protected void atClassObject2(String cname) throws org.hotswap.agent.javassist.compiler.CompileError {
        if (getMajorVersion() < org.hotswap.agent.javassist.bytecode.ClassFile.JAVA_5)
            super.atClassObject2(cname);
        else
            bytecode.addLdc(bytecode.getConstPool().addClassInfo(cname));
    }

    protected void atFieldPlusPlus(int token, boolean isPost,
                                   org.hotswap.agent.javassist.compiler.ast.ASTree oprand, org.hotswap.agent.javassist.compiler.ast.Expr expr, boolean doDup)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtField f = fieldAccess(oprand, false);
        boolean is_static = resultStatic;
        if (!is_static)
            bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.DUP);

        int fi = atFieldRead(f, is_static);
        int t = exprType;
        boolean is2w = is2word(t, arrayDim);

        int dup_code;
        if (is_static)
            dup_code = (is2w ? org.hotswap.agent.javassist.bytecode.Opcode.DUP2 : org.hotswap.agent.javassist.bytecode.Opcode.DUP);
        else
            dup_code = (is2w ? org.hotswap.agent.javassist.bytecode.Opcode.DUP2_X1 : org.hotswap.agent.javassist.bytecode.Opcode.DUP_X1);

        atPlusPlusCore(dup_code, doDup, token, isPost, expr);
        atFieldAssignCore(f, is_static, fi, is2w);
    }

    /* This method also returns a value in resultStatic.
     *
     * @param acceptLength      true if array length is acceptable
     */
    protected org.hotswap.agent.javassist.CtField fieldAccess(org.hotswap.agent.javassist.compiler.ast.ASTree expr, boolean acceptLength)
            throws org.hotswap.agent.javassist.compiler.CompileError {
        if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            String name = ((org.hotswap.agent.javassist.compiler.ast.Member) expr).get();
            org.hotswap.agent.javassist.CtField f = null;
            try {
                f = thisClass.getField(name);
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
                // EXPR might be part of a static member access?
                throw new org.hotswap.agent.javassist.compiler.NoFieldException(name, expr);
            }

            boolean is_static = org.hotswap.agent.javassist.Modifier.isStatic(f.getModifiers());
            if (!is_static)
                if (inStaticMethod)
                    throw new org.hotswap.agent.javassist.compiler.CompileError(
                            "not available in a static method: " + name);
                else
                    bytecode.addAload(0);       // this

            resultStatic = is_static;
            return f;
        } else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) expr;
            int op = e.getOperator();
            if (op == TokenId.MEMBER) {
                /* static member by # (extension by Javassist)
                 * For example, if int.class is parsed, the resulting tree
                 * is (# "java.lang.Integer" "TYPE"). 
                 */
                org.hotswap.agent.javassist.CtField f = resolver.lookupField(((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand1()).get(),
                        (org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2());
                resultStatic = true;
                return f;
            } else if (op == '.') {
                org.hotswap.agent.javassist.CtField f = null;
                try {
                    e.oprand1().accept(this);
                    /* Don't call lookupFieldByJvmName2().
                     * The left operand of . is not a class name but
                     * a normal expression.
                     */
                    if (exprType == TokenId.CLASS && arrayDim == 0)
                        f = resolver.lookupFieldByJvmName(className,
                                (org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2());
                    else if (acceptLength && arrayDim > 0
                            && ((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2()).get().equals("length"))
                        return null;    // expr is an array length.
                    else
                        badLvalue();

                    boolean is_static = org.hotswap.agent.javassist.Modifier.isStatic(f.getModifiers());
                    if (is_static)
                        bytecode.addOpcode(org.hotswap.agent.javassist.bytecode.Opcode.POP);

                    resultStatic = is_static;
                    return f;
                } catch (org.hotswap.agent.javassist.compiler.NoFieldException nfe) {
                    if (nfe.getExpr() != e.oprand1())
                        throw nfe;

                    /* EXPR should be a static field.
                     * If EXPR might be part of a qualified class name,
                     * lookupFieldByJvmName2() throws NoFieldException.
                     */
                    org.hotswap.agent.javassist.compiler.ast.Symbol fname = (org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2();
                    String cname = nfe.getField();
                    f = resolver.lookupFieldByJvmName2(cname, fname, expr);
                    resultStatic = true;
                    return f;
                }
            } else
                badLvalue();
        } else
            badLvalue();

        resultStatic = false;
        return null;    // never reach
    }

    private static void badLvalue() throws org.hotswap.agent.javassist.compiler.CompileError {
        throw new org.hotswap.agent.javassist.compiler.CompileError("bad l-value");
    }

    public org.hotswap.agent.javassist.CtClass[] makeParamList(org.hotswap.agent.javassist.compiler.ast.MethodDecl md) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtClass[] params;
        org.hotswap.agent.javassist.compiler.ast.ASTList plist = md.getParams();
        if (plist == null)
            params = new org.hotswap.agent.javassist.CtClass[0];
        else {
            int i = 0;
            params = new org.hotswap.agent.javassist.CtClass[plist.length()];
            while (plist != null) {
                params[i++] = resolver.lookupClass((org.hotswap.agent.javassist.compiler.ast.Declarator) plist.head());
                plist = plist.tail();
            }
        }

        return params;
    }

    public org.hotswap.agent.javassist.CtClass[] makeThrowsList(org.hotswap.agent.javassist.compiler.ast.MethodDecl md) throws org.hotswap.agent.javassist.compiler.CompileError {
        org.hotswap.agent.javassist.CtClass[] clist;
        org.hotswap.agent.javassist.compiler.ast.ASTList list = md.getThrows();
        if (list == null)
            return null;
        else {
            int i = 0;
            clist = new org.hotswap.agent.javassist.CtClass[list.length()];
            while (list != null) {
                clist[i++] = resolver.lookupClassByName((org.hotswap.agent.javassist.compiler.ast.ASTList) list.head());
                list = list.tail();
            }

            return clist;
        }
    }

    /* Converts a class name into a JVM-internal representation.
     *
     * It may also expand a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    protected String resolveClassName(org.hotswap.agent.javassist.compiler.ast.ASTList name) throws org.hotswap.agent.javassist.compiler.CompileError {
        return resolver.resolveClassName(name);
    }

    /* Expands a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    protected String resolveClassName(String jvmName) throws org.hotswap.agent.javassist.compiler.CompileError {
        return resolver.resolveJvmClassName(jvmName);
    }
}
