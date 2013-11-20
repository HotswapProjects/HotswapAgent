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

public class TypeChecker extends org.hotswap.agent.javassist.compiler.ast.Visitor implements org.hotswap.agent.javassist.bytecode.Opcode, TokenId {
    static final String javaLangObject = "java.lang.Object";
    static final String jvmJavaLangObject = "java/lang/Object";
    static final String jvmJavaLangString = "java/lang/String";
    static final String jvmJavaLangClass = "java/lang/Class";

    /* The following fields are used by atXXX() methods
     * for returning the type of the compiled expression.
     */
    protected int exprType;     // VOID, NULL, CLASS, BOOLEAN, INT, ...
    protected int arrayDim;
    protected String className; // JVM-internal representation

    protected MemberResolver resolver;
    protected org.hotswap.agent.javassist.CtClass thisClass;
    protected org.hotswap.agent.javassist.bytecode.MethodInfo thisMethod;

    public TypeChecker(org.hotswap.agent.javassist.CtClass cc, org.hotswap.agent.javassist.ClassPool cp) {
        resolver = new MemberResolver(cp);
        thisClass = cc;
        thisMethod = null;
    }

    /*
     * Converts an array of tuples of exprType, arrayDim, and className
     * into a String object.
     */
    protected static String argTypesToString(int[] types, int[] dims,
                                             String[] cnames) {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append('(');
        int n = types.length;
        if (n > 0) {
            int i = 0;
            while (true) {
                typeToString(sbuf, types[i], dims[i], cnames[i]);
                if (++i < n)
                    sbuf.append(',');
                else
                    break;
            }
        }

        sbuf.append(')');
        return sbuf.toString();
    }

    /*
     * Converts a tuple of exprType, arrayDim, and className
     * into a String object.
     */
    protected static StringBuffer typeToString(StringBuffer sbuf,
                                               int type, int dim, String cname) {
        String s;
        if (type == CLASS)
            s = MemberResolver.jvmToJavaName(cname);
        else if (type == NULL)
            s = "Object";
        else
            try {
                s = MemberResolver.getTypeName(type);
            } catch (CompileError e) {
                s = "?";
            }

        sbuf.append(s);
        while (dim-- > 0)
            sbuf.append("[]");

        return sbuf;
    }

    /**
     * Records the currently compiled method.
     */
    public void setThisMethod(org.hotswap.agent.javassist.bytecode.MethodInfo m) {
        thisMethod = m;
    }

    protected static void fatal() throws CompileError {
        throw new CompileError("fatal");
    }

    /**
     * Returns the JVM-internal representation of this class name.
     */
    protected String getThisName() {
        return MemberResolver.javaToJvmName(thisClass.getName());
    }

    /**
     * Returns the JVM-internal representation of this super class name.
     */
    protected String getSuperName() throws CompileError {
        return MemberResolver.javaToJvmName(
                MemberResolver.getSuperclass(thisClass).getName());
    }

    /* Converts a class name into a JVM-internal representation.
     *
     * It may also expand a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    protected String resolveClassName(org.hotswap.agent.javassist.compiler.ast.ASTList name) throws CompileError {
        return resolver.resolveClassName(name);
    }

    /* Expands a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    protected String resolveClassName(String jvmName) throws CompileError {
        return resolver.resolveJvmClassName(jvmName);
    }

    public void atNewExpr(org.hotswap.agent.javassist.compiler.ast.NewExpr expr) throws CompileError {
        if (expr.isArray())
            atNewArrayExpr(expr);
        else {
            org.hotswap.agent.javassist.CtClass clazz = resolver.lookupClassByName(expr.getClassName());
            String cname = clazz.getName();
            org.hotswap.agent.javassist.compiler.ast.ASTList args = expr.getArguments();
            atMethodCallCore(clazz, org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit, args);
            exprType = CLASS;
            arrayDim = 0;
            className = MemberResolver.javaToJvmName(cname);
        }
    }

    public void atNewArrayExpr(org.hotswap.agent.javassist.compiler.ast.NewExpr expr) throws CompileError {
        int type = expr.getArrayType();
        org.hotswap.agent.javassist.compiler.ast.ASTList size = expr.getArraySize();
        org.hotswap.agent.javassist.compiler.ast.ASTList classname = expr.getClassName();
        org.hotswap.agent.javassist.compiler.ast.ASTree init = expr.getInitializer();
        if (init != null)
            init.accept(this);

        if (size.length() > 1)
            atMultiNewArray(type, classname, size);
        else {
            org.hotswap.agent.javassist.compiler.ast.ASTree sizeExpr = size.head();
            if (sizeExpr != null)
                sizeExpr.accept(this);

            exprType = type;
            arrayDim = 1;
            if (type == CLASS)
                className = resolveClassName(classname);
            else
                className = null;
        }
    }

    public void atArrayInit(org.hotswap.agent.javassist.compiler.ast.ArrayInit init) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList list = init;
        while (list != null) {
            org.hotswap.agent.javassist.compiler.ast.ASTree h = list.head();
            list = list.tail();
            if (h != null)
                h.accept(this);
        }
    }

    protected void atMultiNewArray(int type, org.hotswap.agent.javassist.compiler.ast.ASTList classname, org.hotswap.agent.javassist.compiler.ast.ASTList size)
            throws CompileError {
        int count, dim;
        dim = size.length();
        for (count = 0; size != null; size = size.tail()) {
            org.hotswap.agent.javassist.compiler.ast.ASTree s = size.head();
            if (s == null)
                break;          // int[][][] a = new int[3][4][];

            ++count;
            s.accept(this);
        }

        exprType = type;
        arrayDim = dim;
        if (type == CLASS)
            className = resolveClassName(classname);
        else
            className = null;
    }

    public void atAssignExpr(org.hotswap.agent.javassist.compiler.ast.AssignExpr expr) throws CompileError {
        // =, %=, &=, *=, /=, +=, -=, ^=, |=, <<=, >>=, >>>=
        int op = expr.getOperator();
        org.hotswap.agent.javassist.compiler.ast.ASTree left = expr.oprand1();
        org.hotswap.agent.javassist.compiler.ast.ASTree right = expr.oprand2();
        if (left instanceof org.hotswap.agent.javassist.compiler.ast.Variable)
            atVariableAssign(expr, op, (org.hotswap.agent.javassist.compiler.ast.Variable) left,
                    ((org.hotswap.agent.javassist.compiler.ast.Variable) left).getDeclarator(),
                    right);
        else {
            if (left instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
                org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) left;
                if (e.getOperator() == ARRAY) {
                    atArrayAssign(expr, op, (org.hotswap.agent.javassist.compiler.ast.Expr) left, right);
                    return;
                }
            }

            atFieldAssign(expr, op, left, right);
        }
    }

    /* op is either =, %=, &=, *=, /=, +=, -=, ^=, |=, <<=, >>=, or >>>=.
     *
     * expr and var can be null.
     */
    private void atVariableAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.Variable var,
                                  org.hotswap.agent.javassist.compiler.ast.Declarator d, org.hotswap.agent.javassist.compiler.ast.ASTree right)
            throws CompileError {
        int varType = d.getType();
        int varArray = d.getArrayDim();
        String varClass = d.getClassName();

        if (op != '=')
            atVariable(var);

        right.accept(this);
        exprType = varType;
        arrayDim = varArray;
        className = varClass;
    }

    private void atArrayAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.Expr array,
                               org.hotswap.agent.javassist.compiler.ast.ASTree right) throws CompileError {
        atArrayRead(array.oprand1(), array.oprand2());
        int aType = exprType;
        int aDim = arrayDim;
        String cname = className;
        right.accept(this);
        exprType = aType;
        arrayDim = aDim;
        className = cname;
    }

    protected void atFieldAssign(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree left, org.hotswap.agent.javassist.compiler.ast.ASTree right)
            throws CompileError {
        org.hotswap.agent.javassist.CtField f = fieldAccess(left);
        atFieldRead(f);
        int fType = exprType;
        int fDim = arrayDim;
        String cname = className;
        right.accept(this);
        exprType = fType;
        arrayDim = fDim;
        className = cname;
    }

    public void atCondExpr(org.hotswap.agent.javassist.compiler.ast.CondExpr expr) throws CompileError {
        booleanExpr(expr.condExpr());
        expr.thenExpr().accept(this);
        int type1 = exprType;
        int dim1 = arrayDim;
        String cname1 = className;
        expr.elseExpr().accept(this);

        if (dim1 == 0 && dim1 == arrayDim)
            if (CodeGen.rightIsStrong(type1, exprType))
                expr.setThen(new org.hotswap.agent.javassist.compiler.ast.CastExpr(exprType, 0, expr.thenExpr()));
            else if (CodeGen.rightIsStrong(exprType, type1)) {
                expr.setElse(new org.hotswap.agent.javassist.compiler.ast.CastExpr(type1, 0, expr.elseExpr()));
                exprType = type1;
            }
    }

    /*
     * If atBinExpr() substitutes a new expression for the original
     * binary-operator expression, it changes the operator name to '+'
     * (if the original is not '+') and sets the new expression to the
     * left-hand-side expression and null to the right-hand-side expression. 
     */
    public void atBinExpr(org.hotswap.agent.javassist.compiler.ast.BinExpr expr) throws CompileError {
        int token = expr.getOperator();
        int k = CodeGen.lookupBinOp(token);
        if (k >= 0) {
            /* arithmetic operators: +, -, *, /, %, |, ^, &, <<, >>, >>>
             */
            if (token == '+') {
                org.hotswap.agent.javassist.compiler.ast.Expr e = atPlusExpr(expr);
                if (e != null) {
                    /* String concatenation has been translated into
                     * an expression using StringBuffer.
                     */
                    e = org.hotswap.agent.javassist.compiler.ast.CallExpr.makeCall(org.hotswap.agent.javassist.compiler.ast.Expr.make('.', e,
                            new org.hotswap.agent.javassist.compiler.ast.Member("toString")), null);
                    expr.setOprand1(e);
                    expr.setOprand2(null);    // <---- look at this!
                    className = jvmJavaLangString;
                }
            } else {
                org.hotswap.agent.javassist.compiler.ast.ASTree left = expr.oprand1();
                org.hotswap.agent.javassist.compiler.ast.ASTree right = expr.oprand2();
                left.accept(this);
                int type1 = exprType;
                right.accept(this);
                if (!isConstant(expr, token, left, right))
                    computeBinExprType(expr, token, type1);
            }
        } else {
            /* equation: &&, ||, ==, !=, <=, >=, <, >
            */
            booleanExpr(expr);
        }
    }

    /* EXPR must be a + expression.
     * atPlusExpr() returns non-null if the given expression is string
     * concatenation.  The returned value is "new StringBuffer().append..".
     */
    private org.hotswap.agent.javassist.compiler.ast.Expr atPlusExpr(org.hotswap.agent.javassist.compiler.ast.BinExpr expr) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree left = expr.oprand1();
        org.hotswap.agent.javassist.compiler.ast.ASTree right = expr.oprand2();
        if (right == null) {
            // this expression has been already type-checked.
            // see atBinExpr() above.
            left.accept(this);
            return null;
        }

        if (isPlusExpr(left)) {
            org.hotswap.agent.javassist.compiler.ast.Expr newExpr = atPlusExpr((org.hotswap.agent.javassist.compiler.ast.BinExpr) left);
            if (newExpr != null) {
                right.accept(this);
                exprType = CLASS;
                arrayDim = 0;
                className = "java/lang/StringBuffer";
                return makeAppendCall(newExpr, right);
            }
        } else
            left.accept(this);

        int type1 = exprType;
        int dim1 = arrayDim;
        String cname = className;
        right.accept(this);

        if (isConstant(expr, '+', left, right))
            return null;

        if ((type1 == CLASS && dim1 == 0 && jvmJavaLangString.equals(cname))
                || (exprType == CLASS && arrayDim == 0
                && jvmJavaLangString.equals(className))) {
            org.hotswap.agent.javassist.compiler.ast.ASTList sbufClass = org.hotswap.agent.javassist.compiler.ast.ASTList.make(new org.hotswap.agent.javassist.compiler.ast.Symbol("java"),
                    new org.hotswap.agent.javassist.compiler.ast.Symbol("lang"), new org.hotswap.agent.javassist.compiler.ast.Symbol("StringBuffer"));
            org.hotswap.agent.javassist.compiler.ast.ASTree e = new org.hotswap.agent.javassist.compiler.ast.NewExpr(sbufClass, null);
            exprType = CLASS;
            arrayDim = 0;
            className = "java/lang/StringBuffer";
            return makeAppendCall(makeAppendCall(e, left), right);
        } else {
            computeBinExprType(expr, '+', type1);
            return null;
        }
    }

    private boolean isConstant(org.hotswap.agent.javassist.compiler.ast.BinExpr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree left,
                               org.hotswap.agent.javassist.compiler.ast.ASTree right) throws CompileError {
        left = stripPlusExpr(left);
        right = stripPlusExpr(right);
        org.hotswap.agent.javassist.compiler.ast.ASTree newExpr = null;
        if (left instanceof org.hotswap.agent.javassist.compiler.ast.StringL && right instanceof org.hotswap.agent.javassist.compiler.ast.StringL && op == '+')
            newExpr = new org.hotswap.agent.javassist.compiler.ast.StringL(((org.hotswap.agent.javassist.compiler.ast.StringL) left).get()
                    + ((org.hotswap.agent.javassist.compiler.ast.StringL) right).get());
        else if (left instanceof org.hotswap.agent.javassist.compiler.ast.IntConst)
            newExpr = ((org.hotswap.agent.javassist.compiler.ast.IntConst) left).compute(op, right);
        else if (left instanceof org.hotswap.agent.javassist.compiler.ast.DoubleConst)
            newExpr = ((org.hotswap.agent.javassist.compiler.ast.DoubleConst) left).compute(op, right);

        if (newExpr == null)
            return false;       // not a constant expression
        else {
            expr.setOperator('+');
            expr.setOprand1(newExpr);
            expr.setOprand2(null);
            newExpr.accept(this);   // for setting exprType, arrayDim, ...
            return true;
        }
    }

    /* CodeGen.atSwitchStmnt() also calls stripPlusExpr().
     */
    static org.hotswap.agent.javassist.compiler.ast.ASTree stripPlusExpr(org.hotswap.agent.javassist.compiler.ast.ASTree expr) {
        if (expr instanceof org.hotswap.agent.javassist.compiler.ast.BinExpr) {
            org.hotswap.agent.javassist.compiler.ast.BinExpr e = (org.hotswap.agent.javassist.compiler.ast.BinExpr) expr;
            if (e.getOperator() == '+' && e.oprand2() == null)
                return e.getLeft();
        } else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {    // note: BinExpr extends Expr.
            org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) expr;
            int op = e.getOperator();
            if (op == MEMBER) {
                org.hotswap.agent.javassist.compiler.ast.ASTree cexpr = getConstantFieldValue((org.hotswap.agent.javassist.compiler.ast.Member) e.oprand2());
                if (cexpr != null)
                    return cexpr;
            } else if (op == '+' && e.getRight() == null)
                return e.getLeft();
        } else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            org.hotswap.agent.javassist.compiler.ast.ASTree cexpr = getConstantFieldValue((org.hotswap.agent.javassist.compiler.ast.Member) expr);
            if (cexpr != null)
                return cexpr;
        }

        return expr;
    }

    /**
     * If MEM is a static final field, this method returns a constant
     * expression representing the value of that field.
     */
    private static org.hotswap.agent.javassist.compiler.ast.ASTree getConstantFieldValue(org.hotswap.agent.javassist.compiler.ast.Member mem) {
        return getConstantFieldValue(mem.getField());
    }

    public static org.hotswap.agent.javassist.compiler.ast.ASTree getConstantFieldValue(org.hotswap.agent.javassist.CtField f) {
        if (f == null)
            return null;

        Object value = f.getConstantValue();
        if (value == null)
            return null;

        if (value instanceof String)
            return new org.hotswap.agent.javassist.compiler.ast.StringL((String) value);
        else if (value instanceof Double || value instanceof Float) {
            int token = (value instanceof Double)
                    ? DoubleConstant : FloatConstant;
            return new org.hotswap.agent.javassist.compiler.ast.DoubleConst(((Number) value).doubleValue(), token);
        } else if (value instanceof Number) {
            int token = (value instanceof Long) ? LongConstant : IntConstant;
            return new org.hotswap.agent.javassist.compiler.ast.IntConst(((Number) value).longValue(), token);
        } else if (value instanceof Boolean)
            return new org.hotswap.agent.javassist.compiler.ast.Keyword(((Boolean) value).booleanValue()
                    ? TokenId.TRUE : TokenId.FALSE);
        else
            return null;
    }

    private static boolean isPlusExpr(org.hotswap.agent.javassist.compiler.ast.ASTree expr) {
        if (expr instanceof org.hotswap.agent.javassist.compiler.ast.BinExpr) {
            org.hotswap.agent.javassist.compiler.ast.BinExpr bexpr = (org.hotswap.agent.javassist.compiler.ast.BinExpr) expr;
            int token = bexpr.getOperator();
            return token == '+';
        }

        return false;
    }

    private static org.hotswap.agent.javassist.compiler.ast.Expr makeAppendCall(org.hotswap.agent.javassist.compiler.ast.ASTree target, org.hotswap.agent.javassist.compiler.ast.ASTree arg) {
        return org.hotswap.agent.javassist.compiler.ast.CallExpr.makeCall(org.hotswap.agent.javassist.compiler.ast.Expr.make('.', target, new org.hotswap.agent.javassist.compiler.ast.Member("append")),
                new org.hotswap.agent.javassist.compiler.ast.ASTList(arg));
    }

    private void computeBinExprType(org.hotswap.agent.javassist.compiler.ast.BinExpr expr, int token, int type1)
            throws CompileError {
        // arrayDim should be 0.
        int type2 = exprType;
        if (token == LSHIFT || token == RSHIFT || token == ARSHIFT)
            exprType = type1;
        else
            insertCast(expr, type1, type2);

        if (CodeGen.isP_INT(exprType))
            exprType = INT;         // type1 may be BYTE, ...
    }

    private void booleanExpr(org.hotswap.agent.javassist.compiler.ast.ASTree expr)
            throws CompileError {
        int op = CodeGen.getCompOperator(expr);
        if (op == EQ) {         // ==, !=, ...
            org.hotswap.agent.javassist.compiler.ast.BinExpr bexpr = (org.hotswap.agent.javassist.compiler.ast.BinExpr) expr;
            bexpr.oprand1().accept(this);
            int type1 = exprType;
            int dim1 = arrayDim;
            bexpr.oprand2().accept(this);
            if (dim1 == 0 && arrayDim == 0)
                insertCast(bexpr, type1, exprType);
        } else if (op == '!')
            ((org.hotswap.agent.javassist.compiler.ast.Expr) expr).oprand1().accept(this);
        else if (op == ANDAND || op == OROR) {
            org.hotswap.agent.javassist.compiler.ast.BinExpr bexpr = (org.hotswap.agent.javassist.compiler.ast.BinExpr) expr;
            bexpr.oprand1().accept(this);
            bexpr.oprand2().accept(this);
        } else                // others
            expr.accept(this);

        exprType = BOOLEAN;
        arrayDim = 0;
    }

    private void insertCast(org.hotswap.agent.javassist.compiler.ast.BinExpr expr, int type1, int type2)
            throws CompileError {
        if (CodeGen.rightIsStrong(type1, type2))
            expr.setLeft(new org.hotswap.agent.javassist.compiler.ast.CastExpr(type2, 0, expr.oprand1()));
        else
            exprType = type1;
    }

    public void atCastExpr(org.hotswap.agent.javassist.compiler.ast.CastExpr expr) throws CompileError {
        String cname = resolveClassName(expr.getClassName());
        expr.getOprand().accept(this);
        exprType = expr.getType();
        arrayDim = expr.getArrayDim();
        className = cname;
    }

    public void atInstanceOfExpr(org.hotswap.agent.javassist.compiler.ast.InstanceOfExpr expr) throws CompileError {
        expr.getOprand().accept(this);
        exprType = BOOLEAN;
        arrayDim = 0;
    }

    public void atExpr(org.hotswap.agent.javassist.compiler.ast.Expr expr) throws CompileError {
        // array access, member access,
        // (unary) +, (unary) -, ++, --, !, ~

        int token = expr.getOperator();
        org.hotswap.agent.javassist.compiler.ast.ASTree oprand = expr.oprand1();
        if (token == '.') {
            String member = ((org.hotswap.agent.javassist.compiler.ast.Symbol) expr.oprand2()).get();
            if (member.equals("length"))
                try {
                    atArrayLength(expr);
                } catch (NoFieldException nfe) {
                    // length might be a class or package name.
                    atFieldRead(expr);
                }
            else if (member.equals("class"))
                atClassObject(expr);  // .class
            else
                atFieldRead(expr);
        } else if (token == MEMBER) {     // field read
            String member = ((org.hotswap.agent.javassist.compiler.ast.Symbol) expr.oprand2()).get();
            if (member.equals("class"))
                atClassObject(expr);  // .class
            else
                atFieldRead(expr);
        } else if (token == ARRAY)
            atArrayRead(oprand, expr.oprand2());
        else if (token == PLUSPLUS || token == MINUSMINUS)
            atPlusPlus(token, oprand, expr);
        else if (token == '!')
            booleanExpr(expr);
        else if (token == CALL)              // method call
            fatal();
        else {
            oprand.accept(this);
            if (!isConstant(expr, token, oprand))
                if (token == '-' || token == '~')
                    if (CodeGen.isP_INT(exprType))
                        exprType = INT;         // type may be BYTE, ...
        }
    }

    private boolean isConstant(org.hotswap.agent.javassist.compiler.ast.Expr expr, int op, org.hotswap.agent.javassist.compiler.ast.ASTree oprand) {
        oprand = stripPlusExpr(oprand);
        if (oprand instanceof org.hotswap.agent.javassist.compiler.ast.IntConst) {
            org.hotswap.agent.javassist.compiler.ast.IntConst c = (org.hotswap.agent.javassist.compiler.ast.IntConst) oprand;
            long v = c.get();
            if (op == '-')
                v = -v;
            else if (op == '~')
                v = ~v;
            else
                return false;

            c.set(v);
        } else if (oprand instanceof org.hotswap.agent.javassist.compiler.ast.DoubleConst) {
            org.hotswap.agent.javassist.compiler.ast.DoubleConst c = (org.hotswap.agent.javassist.compiler.ast.DoubleConst) oprand;
            if (op == '-')
                c.set(-c.get());
            else
                return false;
        } else
            return false;

        expr.setOperator('+');
        return true;
    }

    public void atCallExpr(org.hotswap.agent.javassist.compiler.ast.CallExpr expr) throws CompileError {
        String mname = null;
        org.hotswap.agent.javassist.CtClass targetClass = null;
        org.hotswap.agent.javassist.compiler.ast.ASTree method = expr.oprand1();
        org.hotswap.agent.javassist.compiler.ast.ASTList args = (org.hotswap.agent.javassist.compiler.ast.ASTList) expr.oprand2();

        if (method instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            mname = ((org.hotswap.agent.javassist.compiler.ast.Member) method).get();
            targetClass = thisClass;
        } else if (method instanceof org.hotswap.agent.javassist.compiler.ast.Keyword) {   // constructor
            mname = org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit;        // <init>
            if (((org.hotswap.agent.javassist.compiler.ast.Keyword) method).get() == SUPER)
                targetClass = MemberResolver.getSuperclass(thisClass);
            else
                targetClass = thisClass;
        } else if (method instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) method;
            mname = ((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2()).get();
            int op = e.getOperator();
            if (op == MEMBER)                // static method
                targetClass
                        = resolver.lookupClass(((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand1()).get(),
                        false);
            else if (op == '.') {
                org.hotswap.agent.javassist.compiler.ast.ASTree target = e.oprand1();
                try {
                    target.accept(this);
                } catch (NoFieldException nfe) {
                    if (nfe.getExpr() != target)
                        throw nfe;

                    // it should be a static method.
                    exprType = CLASS;
                    arrayDim = 0;
                    className = nfe.getField(); // JVM-internal
                    e.setOperator(MEMBER);
                    e.setOprand1(new org.hotswap.agent.javassist.compiler.ast.Symbol(MemberResolver.jvmToJavaName(
                            className)));
                }

                if (arrayDim > 0)
                    targetClass = resolver.lookupClass(javaLangObject, true);
                else if (exprType == CLASS /* && arrayDim == 0 */)
                    targetClass = resolver.lookupClassByJvmName(className);
                else
                    badMethod();
            } else
                badMethod();
        } else
            fatal();

        MemberResolver.Method minfo
                = atMethodCallCore(targetClass, mname, args);
        expr.setMethod(minfo);
    }

    private static void badMethod() throws CompileError {
        throw new CompileError("bad method");
    }

    /**
     * @return a pair of the class declaring the invoked method
     * and the MethodInfo of that method.  Never null.
     */
    public MemberResolver.Method atMethodCallCore(org.hotswap.agent.javassist.CtClass targetClass,
                                                  String mname, org.hotswap.agent.javassist.compiler.ast.ASTList args)
            throws CompileError {
        int nargs = getMethodArgsLength(args);
        int[] types = new int[nargs];
        int[] dims = new int[nargs];
        String[] cnames = new String[nargs];
        atMethodArgs(args, types, dims, cnames);

        MemberResolver.Method found
                = resolver.lookupMethod(targetClass, thisClass, thisMethod,
                mname, types, dims, cnames);
        if (found == null) {
            String clazz = targetClass.getName();
            String signature = argTypesToString(types, dims, cnames);
            String msg;
            if (mname.equals(org.hotswap.agent.javassist.bytecode.MethodInfo.nameInit))
                msg = "cannot find constructor " + clazz + signature;
            else
                msg = mname + signature + " not found in " + clazz;

            throw new CompileError(msg);
        }

        String desc = found.info.getDescriptor();
        setReturnType(desc);
        return found;
    }

    public int getMethodArgsLength(org.hotswap.agent.javassist.compiler.ast.ASTList args) {
        return org.hotswap.agent.javassist.compiler.ast.ASTList.length(args);
    }

    public void atMethodArgs(org.hotswap.agent.javassist.compiler.ast.ASTList args, int[] types, int[] dims,
                             String[] cnames) throws CompileError {
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

    void setReturnType(String desc) throws CompileError {
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

            exprType = CLASS;
            className = desc.substring(i + 1, j);
        } else {
            exprType = MemberResolver.descToType(c);
            className = null;
        }
    }

    private void atFieldRead(org.hotswap.agent.javassist.compiler.ast.ASTree expr) throws CompileError {
        atFieldRead(fieldAccess(expr));
    }

    private void atFieldRead(org.hotswap.agent.javassist.CtField f) throws CompileError {
        org.hotswap.agent.javassist.bytecode.FieldInfo finfo = f.getFieldInfo2();
        String type = finfo.getDescriptor();

        int i = 0;
        int dim = 0;
        char c = type.charAt(i);
        while (c == '[') {
            ++dim;
            c = type.charAt(++i);
        }

        arrayDim = dim;
        exprType = MemberResolver.descToType(c);

        if (c == 'L')
            className = type.substring(i + 1, type.indexOf(';', i + 1));
        else
            className = null;
    }

    /* if EXPR is to access a static field, fieldAccess() translates EXPR
     * into an expression using '#' (MEMBER).  For example, it translates
     * java.lang.Integer.TYPE into java.lang.Integer#TYPE.  This translation
     * speeds up type resolution by MemberCodeGen.
     */
    protected org.hotswap.agent.javassist.CtField fieldAccess(org.hotswap.agent.javassist.compiler.ast.ASTree expr) throws CompileError {
        if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Member) {
            org.hotswap.agent.javassist.compiler.ast.Member mem = (org.hotswap.agent.javassist.compiler.ast.Member) expr;
            String name = mem.get();
            try {
                org.hotswap.agent.javassist.CtField f = thisClass.getField(name);
                if (org.hotswap.agent.javassist.Modifier.isStatic(f.getModifiers()))
                    mem.setField(f);

                return f;
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
                // EXPR might be part of a static member access?
                throw new NoFieldException(name, expr);
            }
        } else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) expr;
            int op = e.getOperator();
            if (op == MEMBER) {
                org.hotswap.agent.javassist.compiler.ast.Member mem = (org.hotswap.agent.javassist.compiler.ast.Member) e.oprand2();
                org.hotswap.agent.javassist.CtField f
                        = resolver.lookupField(((org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand1()).get(), mem);
                mem.setField(f);
                return f;
            } else if (op == '.') {
                try {
                    e.oprand1().accept(this);
                } catch (NoFieldException nfe) {
                    if (nfe.getExpr() != e.oprand1())
                        throw nfe;

                    /* EXPR should be a static field.
                     * If EXPR might be part of a qualified class name,
                     * lookupFieldByJvmName2() throws NoFieldException.
                     */
                    return fieldAccess2(e, nfe.getField());
                }

                CompileError err = null;
                try {
                    if (exprType == CLASS && arrayDim == 0)
                        return resolver.lookupFieldByJvmName(className,
                                (org.hotswap.agent.javassist.compiler.ast.Symbol) e.oprand2());
                } catch (CompileError ce) {
                    err = ce;
                }

                /* If a filed name is the same name as a package's,
                 * a static member of a class in that package is not
                 * visible.  For example,
                 *
                 * class Foo {
                 *   int javassist;
                 * }
                 *
                 * It is impossible to add the following method:
                 *
                 * String m() { return CtClass.intType.toString(); }
                 *
                 * because javassist is a field name.  However, this is
                 * often inconvenient, this compiler allows it.  The following
                 * code is for that.
                 */
                org.hotswap.agent.javassist.compiler.ast.ASTree oprnd1 = e.oprand1();
                if (oprnd1 instanceof org.hotswap.agent.javassist.compiler.ast.Symbol)
                    return fieldAccess2(e, ((org.hotswap.agent.javassist.compiler.ast.Symbol) oprnd1).get());

                if (err != null)
                    throw err;
            }
        }

        throw new CompileError("bad filed access");
    }

    private org.hotswap.agent.javassist.CtField fieldAccess2(org.hotswap.agent.javassist.compiler.ast.Expr e, String jvmClassName) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Member fname = (org.hotswap.agent.javassist.compiler.ast.Member) e.oprand2();
        org.hotswap.agent.javassist.CtField f = resolver.lookupFieldByJvmName2(jvmClassName, fname, e);
        e.setOperator(MEMBER);
        e.setOprand1(new org.hotswap.agent.javassist.compiler.ast.Symbol(MemberResolver.jvmToJavaName(jvmClassName)));
        fname.setField(f);
        return f;
    }

    public void atClassObject(org.hotswap.agent.javassist.compiler.ast.Expr expr) throws CompileError {
        exprType = CLASS;
        arrayDim = 0;
        className = jvmJavaLangClass;
    }

    public void atArrayLength(org.hotswap.agent.javassist.compiler.ast.Expr expr) throws CompileError {
        expr.oprand1().accept(this);
        if (arrayDim == 0)
            throw new NoFieldException("length", expr);

        exprType = INT;
        arrayDim = 0;
    }

    public void atArrayRead(org.hotswap.agent.javassist.compiler.ast.ASTree array, org.hotswap.agent.javassist.compiler.ast.ASTree index)
            throws CompileError {
        array.accept(this);
        int type = exprType;
        int dim = arrayDim;
        String cname = className;
        index.accept(this);
        exprType = type;
        arrayDim = dim - 1;
        className = cname;
    }

    private void atPlusPlus(int token, org.hotswap.agent.javassist.compiler.ast.ASTree oprand, org.hotswap.agent.javassist.compiler.ast.Expr expr)
            throws CompileError {
        boolean isPost = oprand == null;        // ++i or i++?
        if (isPost)
            oprand = expr.oprand2();

        if (oprand instanceof org.hotswap.agent.javassist.compiler.ast.Variable) {
            org.hotswap.agent.javassist.compiler.ast.Declarator d = ((org.hotswap.agent.javassist.compiler.ast.Variable) oprand).getDeclarator();
            exprType = d.getType();
            arrayDim = d.getArrayDim();
        } else {
            if (oprand instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
                org.hotswap.agent.javassist.compiler.ast.Expr e = (org.hotswap.agent.javassist.compiler.ast.Expr) oprand;
                if (e.getOperator() == ARRAY) {
                    atArrayRead(e.oprand1(), e.oprand2());
                    // arrayDim should be 0.
                    int t = exprType;
                    if (t == INT || t == BYTE || t == CHAR || t == SHORT)
                        exprType = INT;

                    return;
                }
            }

            atFieldPlusPlus(oprand);
        }
    }

    protected void atFieldPlusPlus(org.hotswap.agent.javassist.compiler.ast.ASTree oprand) throws CompileError {
        org.hotswap.agent.javassist.CtField f = fieldAccess(oprand);
        atFieldRead(f);
        int t = exprType;
        if (t == INT || t == BYTE || t == CHAR || t == SHORT)
            exprType = INT;
    }

    public void atMember(org.hotswap.agent.javassist.compiler.ast.Member mem) throws CompileError {
        atFieldRead(mem);
    }

    public void atVariable(org.hotswap.agent.javassist.compiler.ast.Variable v) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Declarator d = v.getDeclarator();
        exprType = d.getType();
        arrayDim = d.getArrayDim();
        className = d.getClassName();
    }

    public void atKeyword(org.hotswap.agent.javassist.compiler.ast.Keyword k) throws CompileError {
        arrayDim = 0;
        int token = k.get();
        switch (token) {
            case TRUE:
            case FALSE:
                exprType = BOOLEAN;
                break;
            case NULL:
                exprType = NULL;
                break;
            case THIS:
            case SUPER:
                exprType = CLASS;
                if (token == THIS)
                    className = getThisName();
                else
                    className = getSuperName();
                break;
            default:
                fatal();
        }
    }

    public void atStringL(org.hotswap.agent.javassist.compiler.ast.StringL s) throws CompileError {
        exprType = CLASS;
        arrayDim = 0;
        className = jvmJavaLangString;
    }

    public void atIntConst(org.hotswap.agent.javassist.compiler.ast.IntConst i) throws CompileError {
        arrayDim = 0;
        int type = i.getType();
        if (type == IntConstant || type == CharConstant)
            exprType = (type == IntConstant ? INT : CHAR);
        else
            exprType = LONG;
    }

    public void atDoubleConst(org.hotswap.agent.javassist.compiler.ast.DoubleConst d) throws CompileError {
        arrayDim = 0;
        if (d.getType() == DoubleConstant)
            exprType = DOUBLE;
        else
            exprType = FLOAT;
    }
}
