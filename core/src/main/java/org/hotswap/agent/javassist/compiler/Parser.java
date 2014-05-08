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

public final class Parser implements TokenId {
    private Lex lex;

    public Parser(Lex lex) {
        this.lex = lex;
    }

    public boolean hasMore() {
        return lex.lookAhead() >= 0;
    }

    /* member.declaration
     * : method.declaration | field.declaration
     */
    public org.hotswap.agent.javassist.compiler.ast.ASTList parseMember(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList mem = parseMember1(tbl);
        if (mem instanceof org.hotswap.agent.javassist.compiler.ast.MethodDecl)
            return parseMethod2(tbl, (org.hotswap.agent.javassist.compiler.ast.MethodDecl) mem);
        else
            return mem;
    }

    /* A method body is not parsed.
     */
    public org.hotswap.agent.javassist.compiler.ast.ASTList parseMember1(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList mods = parseMemberMods();
        org.hotswap.agent.javassist.compiler.ast.Declarator d;
        boolean isConstructor = false;
        if (lex.lookAhead() == Identifier && lex.lookAhead(1) == '(') {
            d = new org.hotswap.agent.javassist.compiler.ast.Declarator(VOID, 0);
            isConstructor = true;
        } else
            d = parseFormalType(tbl);

        if (lex.get() != Identifier)
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        String name;
        if (isConstructor)
            name = org.hotswap.agent.javassist.compiler.ast.MethodDecl.initName;
        else
            name = lex.getString();

        d.setVariable(new org.hotswap.agent.javassist.compiler.ast.Symbol(name));
        if (isConstructor || lex.lookAhead() == '(')
            return parseMethod1(tbl, isConstructor, mods, d);
        else
            return parseField(tbl, mods, d);
    }

    /* field.declaration
     *  : member.modifiers
     *    formal.type Identifier
     *    [ "=" expression ] ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.FieldDecl parseField(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.ASTList mods,
                                                                          org.hotswap.agent.javassist.compiler.ast.Declarator d) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = null;
        if (lex.lookAhead() == '=') {
            lex.get();
            expr = parseExpression(tbl);
        }

        int c = lex.get();
        if (c == ';')
            return new org.hotswap.agent.javassist.compiler.ast.FieldDecl(mods, new org.hotswap.agent.javassist.compiler.ast.ASTList(d, new org.hotswap.agent.javassist.compiler.ast.ASTList(expr)));
        else if (c == ',')
            throw new CompileError(
                    "only one field can be declared in one declaration", lex);
        else
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);
    }

    /* method.declaration
     *  : member.modifiers
     *    [ formal.type ]
     *    Identifier "(" [ formal.parameter ( "," formal.parameter )* ] ")"
     *    array.dimension
     *    [ THROWS class.type ( "," class.type ) ]
     *    ( block.statement | ";" )
     *
     * Note that a method body is not parsed.
     */
    private org.hotswap.agent.javassist.compiler.ast.MethodDecl parseMethod1(SymbolTable tbl, boolean isConstructor,
                                                                             org.hotswap.agent.javassist.compiler.ast.ASTList mods, org.hotswap.agent.javassist.compiler.ast.Declarator d)
            throws CompileError {
        if (lex.get() != '(')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.ASTList parms = null;
        if (lex.lookAhead() != ')')
            while (true) {
                parms = org.hotswap.agent.javassist.compiler.ast.ASTList.append(parms, parseFormalParam(tbl));
                int t = lex.lookAhead();
                if (t == ',')
                    lex.get();
                else if (t == ')')
                    break;
            }

        lex.get();      // ')'
        d.addArrayDim(parseArrayDimension());
        if (isConstructor && d.getArrayDim() > 0)
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.ASTList throwsList = null;
        if (lex.lookAhead() == THROWS) {
            lex.get();
            while (true) {
                throwsList = org.hotswap.agent.javassist.compiler.ast.ASTList.append(throwsList, parseClassType(tbl));
                if (lex.lookAhead() == ',')
                    lex.get();
                else
                    break;
            }
        }

        return new org.hotswap.agent.javassist.compiler.ast.MethodDecl(mods, new org.hotswap.agent.javassist.compiler.ast.ASTList(d,
                org.hotswap.agent.javassist.compiler.ast.ASTList.make(parms, throwsList, null)));
    }

    /* Parses a method body.
     */
    public org.hotswap.agent.javassist.compiler.ast.MethodDecl parseMethod2(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.MethodDecl md)
            throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Stmnt body = null;
        if (lex.lookAhead() == ';')
            lex.get();
        else {
            body = parseBlock(tbl);
            if (body == null)
                body = new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK);
        }

        md.sublist(4).setHead(body);
        return md;
    }

    /* member.modifiers
     *  : ( FINAL | SYNCHRONIZED | ABSTRACT
     *    | PUBLIC | PROTECTED | PRIVATE | STATIC
     *    | VOLATILE | TRANSIENT | STRICT )*
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTList parseMemberMods() {
        int t;
        org.hotswap.agent.javassist.compiler.ast.ASTList list = null;
        while (true) {
            t = lex.lookAhead();
            if (t == ABSTRACT || t == FINAL || t == PUBLIC || t == PROTECTED
                    || t == PRIVATE || t == SYNCHRONIZED || t == STATIC
                    || t == VOLATILE || t == TRANSIENT || t == STRICT)
                list = new org.hotswap.agent.javassist.compiler.ast.ASTList(new org.hotswap.agent.javassist.compiler.ast.Keyword(lex.get()), list);
            else
                break;
        }

        return list;
    }

    /* formal.type : ( build-in-type | class.type ) array.dimension
     */
    private org.hotswap.agent.javassist.compiler.ast.Declarator parseFormalType(SymbolTable tbl) throws CompileError {
        int t = lex.lookAhead();
        if (isBuiltinType(t) || t == VOID) {
            lex.get();  // primitive type
            int dim = parseArrayDimension();
            return new org.hotswap.agent.javassist.compiler.ast.Declarator(t, dim);
        } else {
            org.hotswap.agent.javassist.compiler.ast.ASTList name = parseClassType(tbl);
            int dim = parseArrayDimension();
            return new org.hotswap.agent.javassist.compiler.ast.Declarator(name, dim);
        }
    }

    private static boolean isBuiltinType(int t) {
        return (t == BOOLEAN || t == BYTE || t == CHAR || t == SHORT
                || t == INT || t == LONG || t == FLOAT || t == DOUBLE);
    }

    /* formal.parameter : formal.type Identifier array.dimension
     */
    private org.hotswap.agent.javassist.compiler.ast.Declarator parseFormalParam(SymbolTable tbl)
            throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Declarator d = parseFormalType(tbl);
        if (lex.get() != Identifier)
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        String name = lex.getString();
        d.setVariable(new org.hotswap.agent.javassist.compiler.ast.Symbol(name));
        d.addArrayDim(parseArrayDimension());
        tbl.append(name, d);
        return d;
    }

    /* statement : [ label ":" ]* labeled.statement
     *
     * labeled.statement
     *          : block.statement
     *          | if.statement
     *          | while.statement
     *          | do.statement
     *          | for.statement
     *          | switch.statement
     *          | try.statement
     *          | return.statement
     *          | thorw.statement
     *          | break.statement
     *          | continue.statement
     *          | declaration.or.expression
     *          | ";"
     *
     * This method may return null (empty statement).
     */
    public org.hotswap.agent.javassist.compiler.ast.Stmnt parseStatement(SymbolTable tbl)
            throws CompileError {
        int t = lex.lookAhead();
        if (t == '{')
            return parseBlock(tbl);
        else if (t == ';') {
            lex.get();
            return new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK);    // empty statement
        } else if (t == Identifier && lex.lookAhead(1) == ':') {
            lex.get();  // Identifier
            String label = lex.getString();
            lex.get();  // ':'
            return org.hotswap.agent.javassist.compiler.ast.Stmnt.make(LABEL, new org.hotswap.agent.javassist.compiler.ast.Symbol(label), parseStatement(tbl));
        } else if (t == IF)
            return parseIf(tbl);
        else if (t == WHILE)
            return parseWhile(tbl);
        else if (t == DO)
            return parseDo(tbl);
        else if (t == FOR)
            return parseFor(tbl);
        else if (t == TRY)
            return parseTry(tbl);
        else if (t == SWITCH)
            return parseSwitch(tbl);
        else if (t == SYNCHRONIZED)
            return parseSynchronized(tbl);
        else if (t == RETURN)
            return parseReturn(tbl);
        else if (t == THROW)
            return parseThrow(tbl);
        else if (t == BREAK)
            return parseBreak(tbl);
        else if (t == CONTINUE)
            return parseContinue(tbl);
        else
            return parseDeclarationOrExpression(tbl, false);
    }

    /* block.statement : "{" statement* "}"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseBlock(SymbolTable tbl) throws CompileError {
        if (lex.get() != '{')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.Stmnt body = null;
        SymbolTable tbl2 = new SymbolTable(tbl);
        while (lex.lookAhead() != '}') {
            org.hotswap.agent.javassist.compiler.ast.Stmnt s = parseStatement(tbl2);
            if (s != null)
                body = (org.hotswap.agent.javassist.compiler.ast.Stmnt) org.hotswap.agent.javassist.compiler.ast.ASTList.concat(body, new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK, s));
        }

        lex.get();      // '}'
        if (body == null)
            return new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK);    // empty block
        else
            return body;
    }

    /* if.statement : IF "(" expression ")" statement
     *                [ ELSE statement ]
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseIf(SymbolTable tbl) throws CompileError {
        int t = lex.get();      // IF
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseParExpression(tbl);
        org.hotswap.agent.javassist.compiler.ast.Stmnt thenp = parseStatement(tbl);
        org.hotswap.agent.javassist.compiler.ast.Stmnt elsep;
        if (lex.lookAhead() == ELSE) {
            lex.get();
            elsep = parseStatement(tbl);
        } else
            elsep = null;

        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr, new org.hotswap.agent.javassist.compiler.ast.ASTList(thenp, new org.hotswap.agent.javassist.compiler.ast.ASTList(elsep)));
    }

    /* while.statement : WHILE "(" expression ")" statement
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseWhile(SymbolTable tbl)
            throws CompileError {
        int t = lex.get();      // WHILE
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseParExpression(tbl);
        org.hotswap.agent.javassist.compiler.ast.Stmnt body = parseStatement(tbl);
        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr, body);
    }

    /* do.statement : DO statement WHILE "(" expression ")" ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseDo(SymbolTable tbl) throws CompileError {
        int t = lex.get();      // DO
        org.hotswap.agent.javassist.compiler.ast.Stmnt body = parseStatement(tbl);
        if (lex.get() != WHILE || lex.get() != '(')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseExpression(tbl);
        if (lex.get() != ')' || lex.get() != ';')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr, body);
    }

    /* for.statement : FOR "(" decl.or.expr expression ";" expression ")"
     *                 statement
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseFor(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Stmnt expr1, expr3;
        org.hotswap.agent.javassist.compiler.ast.ASTree expr2;
        int t = lex.get();      // FOR

        SymbolTable tbl2 = new SymbolTable(tbl);

        if (lex.get() != '(')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        if (lex.lookAhead() == ';') {
            lex.get();
            expr1 = null;
        } else
            expr1 = parseDeclarationOrExpression(tbl2, true);

        if (lex.lookAhead() == ';')
            expr2 = null;
        else
            expr2 = parseExpression(tbl2);

        if (lex.get() != ';')
            throw new CompileError("; is missing", lex);

        if (lex.lookAhead() == ')')
            expr3 = null;
        else
            expr3 = parseExprList(tbl2);

        if (lex.get() != ')')
            throw new CompileError(") is missing", lex);

        org.hotswap.agent.javassist.compiler.ast.Stmnt body = parseStatement(tbl2);
        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr1, new org.hotswap.agent.javassist.compiler.ast.ASTList(expr2,
                new org.hotswap.agent.javassist.compiler.ast.ASTList(expr3, body)));
    }

    /* switch.statement : SWITCH "(" expression ")" "{" switch.block "}"
     *
     * swtich.block : ( switch.label statement* )*
     *
     * swtich.label : DEFAULT ":"
     *              | CASE const.expression ":"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseSwitch(SymbolTable tbl) throws CompileError {
        int t = lex.get();    // SWITCH
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseParExpression(tbl);
        org.hotswap.agent.javassist.compiler.ast.Stmnt body = parseSwitchBlock(tbl);
        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr, body);
    }

    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseSwitchBlock(SymbolTable tbl) throws CompileError {
        if (lex.get() != '{')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        SymbolTable tbl2 = new SymbolTable(tbl);
        org.hotswap.agent.javassist.compiler.ast.Stmnt s = parseStmntOrCase(tbl2);
        if (s == null)
            throw new CompileError("empty switch block", lex);

        int op = s.getOperator();
        if (op != CASE && op != DEFAULT)
            throw new CompileError("no case or default in a switch block",
                    lex);

        org.hotswap.agent.javassist.compiler.ast.Stmnt body = new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK, s);
        while (lex.lookAhead() != '}') {
            org.hotswap.agent.javassist.compiler.ast.Stmnt s2 = parseStmntOrCase(tbl2);
            if (s2 != null) {
                int op2 = s2.getOperator();
                if (op2 == CASE || op2 == DEFAULT) {
                    body = (org.hotswap.agent.javassist.compiler.ast.Stmnt) org.hotswap.agent.javassist.compiler.ast.ASTList.concat(body, new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK, s2));
                    s = s2;
                } else
                    s = (org.hotswap.agent.javassist.compiler.ast.Stmnt) org.hotswap.agent.javassist.compiler.ast.ASTList.concat(s, new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK, s2));
            }
        }

        lex.get();      // '}'
        return body;
    }

    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseStmntOrCase(SymbolTable tbl) throws CompileError {
        int t = lex.lookAhead();
        if (t != CASE && t != DEFAULT)
            return parseStatement(tbl);

        lex.get();
        org.hotswap.agent.javassist.compiler.ast.Stmnt s;
        if (t == CASE)
            s = new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, parseExpression(tbl));
        else
            s = new org.hotswap.agent.javassist.compiler.ast.Stmnt(DEFAULT);

        if (lex.get() != ':')
            throw new CompileError(": is missing", lex);

        return s;
    }

    /* synchronized.statement :
     *     SYNCHRONIZED "(" expression ")" block.statement
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseSynchronized(SymbolTable tbl) throws CompileError {
        int t = lex.get();    // SYNCHRONIZED
        if (lex.get() != '(')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseExpression(tbl);
        if (lex.get() != ')')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.Stmnt body = parseBlock(tbl);
        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr, body);
    }

    /* try.statement
     * : TRY block.statement
     *   [ CATCH "(" class.type Identifier ")" block.statement ]*
     *   [ FINALLY block.statement ]*
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseTry(SymbolTable tbl) throws CompileError {
        lex.get();      // TRY
        org.hotswap.agent.javassist.compiler.ast.Stmnt block = parseBlock(tbl);
        org.hotswap.agent.javassist.compiler.ast.ASTList catchList = null;
        while (lex.lookAhead() == CATCH) {
            lex.get();  // CATCH
            if (lex.get() != '(')
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

            SymbolTable tbl2 = new SymbolTable(tbl);
            org.hotswap.agent.javassist.compiler.ast.Declarator d = parseFormalParam(tbl2);
            if (d.getArrayDim() > 0 || d.getType() != CLASS)
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

            if (lex.get() != ')')
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

            org.hotswap.agent.javassist.compiler.ast.Stmnt b = parseBlock(tbl2);
            catchList = org.hotswap.agent.javassist.compiler.ast.ASTList.append(catchList, new org.hotswap.agent.javassist.compiler.ast.Pair(d, b));
        }

        org.hotswap.agent.javassist.compiler.ast.Stmnt finallyBlock = null;
        if (lex.lookAhead() == FINALLY) {
            lex.get();  // FINALLY
            finallyBlock = parseBlock(tbl);
        }

        return org.hotswap.agent.javassist.compiler.ast.Stmnt.make(TRY, block, catchList, finallyBlock);
    }

    /* return.statement : RETURN [ expression ] ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseReturn(SymbolTable tbl) throws CompileError {
        int t = lex.get();      // RETURN
        org.hotswap.agent.javassist.compiler.ast.Stmnt s = new org.hotswap.agent.javassist.compiler.ast.Stmnt(t);
        if (lex.lookAhead() != ';')
            s.setLeft(parseExpression(tbl));

        if (lex.get() != ';')
            throw new CompileError("; is missing", lex);

        return s;
    }

    /* throw.statement : THROW expression ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseThrow(SymbolTable tbl) throws CompileError {
        int t = lex.get();      // THROW
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseExpression(tbl);
        if (lex.get() != ';')
            throw new CompileError("; is missing", lex);

        return new org.hotswap.agent.javassist.compiler.ast.Stmnt(t, expr);
    }

    /* break.statement : BREAK [ Identifier ] ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseBreak(SymbolTable tbl)
            throws CompileError {
        return parseContinue(tbl);
    }

    /* continue.statement : CONTINUE [ Identifier ] ";"
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseContinue(SymbolTable tbl)
            throws CompileError {
        int t = lex.get();      // CONTINUE
        org.hotswap.agent.javassist.compiler.ast.Stmnt s = new org.hotswap.agent.javassist.compiler.ast.Stmnt(t);
        int t2 = lex.get();
        if (t2 == Identifier) {
            s.setLeft(new org.hotswap.agent.javassist.compiler.ast.Symbol(lex.getString()));
            t2 = lex.get();
        }

        if (t2 != ';')
            throw new CompileError("; is missing", lex);

        return s;
    }

    /* declaration.or.expression
     *      : [ FINAL ] built-in-type array.dimension declarators
     *      | [ FINAL ] class.type array.dimension declarators
     *      | expression ';'
     *      | expr.list ';'             if exprList is true
     *
     * Note: FINAL is currently ignored.  This must be fixed
     * in future.
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseDeclarationOrExpression(SymbolTable tbl,
                                                                                        boolean exprList)
            throws CompileError {
        int t = lex.lookAhead();
        while (t == FINAL) {
            lex.get();
            t = lex.lookAhead();
        }

        if (isBuiltinType(t)) {
            t = lex.get();
            int dim = parseArrayDimension();
            return parseDeclarators(tbl, new org.hotswap.agent.javassist.compiler.ast.Declarator(t, dim));
        } else if (t == Identifier) {
            int i = nextIsClassType(0);
            if (i >= 0)
                if (lex.lookAhead(i) == Identifier) {
                    org.hotswap.agent.javassist.compiler.ast.ASTList name = parseClassType(tbl);
                    int dim = parseArrayDimension();
                    return parseDeclarators(tbl, new org.hotswap.agent.javassist.compiler.ast.Declarator(name, dim));
                }
        }

        org.hotswap.agent.javassist.compiler.ast.Stmnt expr;
        if (exprList)
            expr = parseExprList(tbl);
        else
            expr = new org.hotswap.agent.javassist.compiler.ast.Stmnt(EXPR, parseExpression(tbl));

        if (lex.get() != ';')
            throw new CompileError("; is missing", lex);

        return expr;
    }

    /* expr.list : ( expression ',')* expression
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseExprList(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Stmnt expr = null;
        for (; ; ) {
            org.hotswap.agent.javassist.compiler.ast.Stmnt e = new org.hotswap.agent.javassist.compiler.ast.Stmnt(EXPR, parseExpression(tbl));
            expr = (org.hotswap.agent.javassist.compiler.ast.Stmnt) org.hotswap.agent.javassist.compiler.ast.ASTList.concat(expr, new org.hotswap.agent.javassist.compiler.ast.Stmnt(BLOCK, e));
            if (lex.lookAhead() == ',')
                lex.get();
            else
                return expr;
        }
    }

    /* declarators : declarator [ ',' declarator ]* ';'
     */
    private org.hotswap.agent.javassist.compiler.ast.Stmnt parseDeclarators(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.Declarator d)
            throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.Stmnt decl = null;
        for (; ; ) {
            decl = (org.hotswap.agent.javassist.compiler.ast.Stmnt) org.hotswap.agent.javassist.compiler.ast.ASTList.concat(decl,
                    new org.hotswap.agent.javassist.compiler.ast.Stmnt(DECL, parseDeclarator(tbl, d)));
            int t = lex.get();
            if (t == ';')
                return decl;
            else if (t != ',')
                throw new CompileError("; is missing", lex);
        }
    }

    /* declarator : Identifier array.dimension [ '=' initializer ]
     */
    private org.hotswap.agent.javassist.compiler.ast.Declarator parseDeclarator(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.Declarator d)
            throws CompileError {
        if (lex.get() != Identifier || d.getType() == VOID)
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        String name = lex.getString();
        org.hotswap.agent.javassist.compiler.ast.Symbol symbol = new org.hotswap.agent.javassist.compiler.ast.Symbol(name);
        int dim = parseArrayDimension();
        org.hotswap.agent.javassist.compiler.ast.ASTree init = null;
        if (lex.lookAhead() == '=') {
            lex.get();
            init = parseInitializer(tbl);
        }

        org.hotswap.agent.javassist.compiler.ast.Declarator decl = d.make(symbol, dim, init);
        tbl.append(name, decl);
        return decl;
    }

    /* initializer : expression | array.initializer
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseInitializer(SymbolTable tbl) throws CompileError {
        if (lex.lookAhead() == '{')
            return parseArrayInitializer(tbl);
        else
            return parseExpression(tbl);
    }

    /* array.initializer :
     *  '{' (( array.initializer | expression ) ',')* '}'
     */
    private org.hotswap.agent.javassist.compiler.ast.ArrayInit parseArrayInitializer(SymbolTable tbl)
            throws CompileError {
        lex.get();      // '{'
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseExpression(tbl);
        org.hotswap.agent.javassist.compiler.ast.ArrayInit init = new org.hotswap.agent.javassist.compiler.ast.ArrayInit(expr);
        while (lex.lookAhead() == ',') {
            lex.get();
            expr = parseExpression(tbl);
            org.hotswap.agent.javassist.compiler.ast.ASTList.append(init, expr);
        }

        if (lex.get() != '}')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        return init;
    }

    /* par.expression : '(' expression ')'
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseParExpression(SymbolTable tbl) throws CompileError {
        if (lex.get() != '(')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseExpression(tbl);
        if (lex.get() != ')')
            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

        return expr;
    }

    /* expression : conditional.expr
     *            | conditional.expr assign.op expression (right-to-left)
     */
    public org.hotswap.agent.javassist.compiler.ast.ASTree parseExpression(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree left = parseConditionalExpr(tbl);
        if (!isAssignOp(lex.lookAhead()))
            return left;

        int t = lex.get();
        org.hotswap.agent.javassist.compiler.ast.ASTree right = parseExpression(tbl);
        return org.hotswap.agent.javassist.compiler.ast.AssignExpr.makeAssign(t, left, right);
    }

    private static boolean isAssignOp(int t) {
        return t == '=' || t == MOD_E || t == AND_E
                || t == MUL_E || t == PLUS_E || t == MINUS_E || t == DIV_E
                || t == EXOR_E || t == OR_E || t == LSHIFT_E
                || t == RSHIFT_E || t == ARSHIFT_E;
    }

    /* conditional.expr                 (right-to-left)
     *     : logical.or.expr [ '?' expression ':' conditional.expr ]
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseConditionalExpr(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree cond = parseBinaryExpr(tbl);
        if (lex.lookAhead() == '?') {
            lex.get();
            org.hotswap.agent.javassist.compiler.ast.ASTree thenExpr = parseExpression(tbl);
            if (lex.get() != ':')
                throw new CompileError(": is missing", lex);

            org.hotswap.agent.javassist.compiler.ast.ASTree elseExpr = parseExpression(tbl);
            return new org.hotswap.agent.javassist.compiler.ast.CondExpr(cond, thenExpr, elseExpr);
        } else
            return cond;
    }

    /* logical.or.expr          10 (operator precedence)
     * : logical.and.expr
     * | logical.or.expr OROR logical.and.expr          left-to-right
     *
     * logical.and.expr         9
     * : inclusive.or.expr
     * | logical.and.expr ANDAND inclusive.or.expr
     *
     * inclusive.or.expr        8
     * : exclusive.or.expr
     * | inclusive.or.expr "|" exclusive.or.expr
     *
     * exclusive.or.expr        7
     *  : and.expr
     * | exclusive.or.expr "^" and.expr
     *
     * and.expr                 6
     * : equality.expr
     * | and.expr "&" equality.expr
     *
     * equality.expr            5
     * : relational.expr
     * | equality.expr (EQ | NEQ) relational.expr
     *
     * relational.expr          4
     * : shift.expr
     * | relational.expr (LE | GE | "<" | ">") shift.expr
     * | relational.expr INSTANCEOF class.type ("[" "]")*
     *
     * shift.expr               3
     * : additive.expr
     * | shift.expr (LSHIFT | RSHIFT | ARSHIFT) additive.expr
     *
     * additive.expr            2
     * : multiply.expr
     * | additive.expr ("+" | "-") multiply.expr
     *
     * multiply.expr            1
     * : unary.expr
     * | multiply.expr ("*" | "/" | "%") unary.expr
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseBinaryExpr(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parseUnaryExpr(tbl);
        for (; ; ) {
            int t = lex.lookAhead();
            int p = getOpPrecedence(t);
            if (p == 0)
                return expr;
            else
                expr = binaryExpr2(tbl, expr, p);
        }
    }

    private org.hotswap.agent.javassist.compiler.ast.ASTree parseInstanceOf(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.ASTree expr)
            throws CompileError {
        int t = lex.lookAhead();
        if (isBuiltinType(t)) {
            lex.get();  // primitive type
            int dim = parseArrayDimension();
            return new org.hotswap.agent.javassist.compiler.ast.InstanceOfExpr(t, dim, expr);
        } else {
            org.hotswap.agent.javassist.compiler.ast.ASTList name = parseClassType(tbl);
            int dim = parseArrayDimension();
            return new org.hotswap.agent.javassist.compiler.ast.InstanceOfExpr(name, dim, expr);
        }
    }

    private org.hotswap.agent.javassist.compiler.ast.ASTree binaryExpr2(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.ASTree expr, int prec)
            throws CompileError {
        int t = lex.get();
        if (t == INSTANCEOF)
            return parseInstanceOf(tbl, expr);

        org.hotswap.agent.javassist.compiler.ast.ASTree expr2 = parseUnaryExpr(tbl);
        for (; ; ) {
            int t2 = lex.lookAhead();
            int p2 = getOpPrecedence(t2);
            if (p2 != 0 && prec > p2)
                expr2 = binaryExpr2(tbl, expr2, p2);
            else
                return org.hotswap.agent.javassist.compiler.ast.BinExpr.makeBin(t, expr, expr2);
        }
    }

    // !"#$%&'(    )*+,-./0    12345678    9:;<=>?
    private static final int[] binaryOpPrecedence
            = {0, 0, 0, 0, 1, 6, 0, 0,
            0, 1, 2, 0, 2, 0, 1, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 4, 0, 4, 0};

    private int getOpPrecedence(int c) {
        if ('!' <= c && c <= '?')
            return binaryOpPrecedence[c - '!'];
        else if (c == '^')
            return 7;
        else if (c == '|')
            return 8;
        else if (c == ANDAND)
            return 9;
        else if (c == OROR)
            return 10;
        else if (c == EQ || c == NEQ)
            return 5;
        else if (c == LE || c == GE || c == INSTANCEOF)
            return 4;
        else if (c == LSHIFT || c == RSHIFT || c == ARSHIFT)
            return 3;
        else
            return 0;   // not a binary operator
    }

    /* unary.expr : "++"|"--" unary.expr
                  | "+"|"-" unary.expr
                  | "!"|"~" unary.expr
                  | cast.expr
                  | postfix.expr

       unary.expr.not.plus.minus is a unary expression starting without
       "+", "-", "++", or "--".
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseUnaryExpr(SymbolTable tbl) throws CompileError {
        int t;
        switch (lex.lookAhead()) {
            case '+':
            case '-':
            case PLUSPLUS:
            case MINUSMINUS:
            case '!':
            case '~':
                t = lex.get();
                if (t == '-') {
                    int t2 = lex.lookAhead();
                    switch (t2) {
                        case LongConstant:
                        case IntConstant:
                        case CharConstant:
                            lex.get();
                            return new org.hotswap.agent.javassist.compiler.ast.IntConst(-lex.getLong(), t2);
                        case DoubleConstant:
                        case FloatConstant:
                            lex.get();
                            return new org.hotswap.agent.javassist.compiler.ast.DoubleConst(-lex.getDouble(), t2);
                        default:
                            break;
                    }
                }

                return org.hotswap.agent.javassist.compiler.ast.Expr.make(t, parseUnaryExpr(tbl));
            case '(':
                return parseCast(tbl);
            default:
                return parsePostfix(tbl);
        }
    }

    /* cast.expr : "(" builtin.type ("[" "]")* ")" unary.expr
                 | "(" class.type ("[" "]")* ")" unary.expr2

       unary.expr2 is a unary.expr beginning with "(", NULL, StringL,
       Identifier, THIS, SUPER, or NEW.

       Either "(int.class)" or "(String[].class)" is a not cast expression.
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseCast(SymbolTable tbl) throws CompileError {
        int t = lex.lookAhead(1);
        if (isBuiltinType(t) && nextIsBuiltinCast()) {
            lex.get();  // '('
            lex.get();  // primitive type
            int dim = parseArrayDimension();
            if (lex.get() != ')')
                throw new CompileError(") is missing", lex);

            return new org.hotswap.agent.javassist.compiler.ast.CastExpr(t, dim, parseUnaryExpr(tbl));
        } else if (t == Identifier && nextIsClassCast()) {
            lex.get();  // '('
            org.hotswap.agent.javassist.compiler.ast.ASTList name = parseClassType(tbl);
            int dim = parseArrayDimension();
            if (lex.get() != ')')
                throw new CompileError(") is missing", lex);

            return new org.hotswap.agent.javassist.compiler.ast.CastExpr(name, dim, parseUnaryExpr(tbl));
        } else
            return parsePostfix(tbl);
    }

    private boolean nextIsBuiltinCast() {
        int t;
        int i = 2;
        while ((t = lex.lookAhead(i++)) == '[')
            if (lex.lookAhead(i++) != ']')
                return false;

        return lex.lookAhead(i - 1) == ')';
    }

    private boolean nextIsClassCast() {
        int i = nextIsClassType(1);
        if (i < 0)
            return false;

        int t = lex.lookAhead(i);
        if (t != ')')
            return false;

        t = lex.lookAhead(i + 1);
        return t == '(' || t == NULL || t == StringL
                || t == Identifier || t == THIS || t == SUPER || t == NEW
                || t == TRUE || t == FALSE || t == LongConstant
                || t == IntConstant || t == CharConstant
                || t == DoubleConstant || t == FloatConstant;
    }

    private int nextIsClassType(int i) {
        int t;
        while (lex.lookAhead(++i) == '.')
            if (lex.lookAhead(++i) != Identifier)
                return -1;

        while ((t = lex.lookAhead(i++)) == '[')
            if (lex.lookAhead(i++) != ']')
                return -1;

        return i - 1;
    }

    /* array.dimension : [ "[" "]" ]*
     */
    private int parseArrayDimension() throws CompileError {
        int arrayDim = 0;
        while (lex.lookAhead() == '[') {
            ++arrayDim;
            lex.get();
            if (lex.get() != ']')
                throw new CompileError("] is missing", lex);
        }

        return arrayDim;
    }

    /* class.type : Identifier ( "." Identifier )*
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTList parseClassType(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList list = null;
        for (; ; ) {
            if (lex.get() != Identifier)
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

            list = org.hotswap.agent.javassist.compiler.ast.ASTList.append(list, new org.hotswap.agent.javassist.compiler.ast.Symbol(lex.getString()));
            if (lex.lookAhead() == '.')
                lex.get();
            else
                break;
        }

        return list;
    }

    /* postfix.expr : number.literal
     *              | primary.expr
     *              | method.expr
     *              | postfix.expr "++" | "--"
     *              | postfix.expr "[" array.size "]"
     *              | postfix.expr "." Identifier
     *              | postfix.expr ( "[" "]" )* "." CLASS
     *              | postfix.expr "#" Identifier
     *
     * "#" is not an operator of regular Java.  It separates
     * a class name and a member name in an expression for static member
     * access.  For example,
     *     java.lang.Integer.toString(3)        in regular Java
     * can be written like this:
     *     java.lang.Integer#toString(3)        for this compiler.
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parsePostfix(SymbolTable tbl) throws CompileError {
        int token = lex.lookAhead();
        switch (token) {    // see also parseUnaryExpr()
            case LongConstant:
            case IntConstant:
            case CharConstant:
                lex.get();
                return new org.hotswap.agent.javassist.compiler.ast.IntConst(lex.getLong(), token);
            case DoubleConstant:
            case FloatConstant:
                lex.get();
                return new org.hotswap.agent.javassist.compiler.ast.DoubleConst(lex.getDouble(), token);
            default:
                break;
        }

        String str;
        org.hotswap.agent.javassist.compiler.ast.ASTree index;
        org.hotswap.agent.javassist.compiler.ast.ASTree expr = parsePrimaryExpr(tbl);
        int t;
        while (true) {
            switch (lex.lookAhead()) {
                case '(':
                    expr = parseMethodCall(tbl, expr);
                    break;
                case '[':
                    if (lex.lookAhead(1) == ']') {
                        int dim = parseArrayDimension();
                        if (lex.get() != '.' || lex.get() != CLASS)
                            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

                        expr = parseDotClass(expr, dim);
                    } else {
                        index = parseArrayIndex(tbl);
                        if (index == null)
                            throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);

                        expr = org.hotswap.agent.javassist.compiler.ast.Expr.make(ARRAY, expr, index);
                    }
                    break;
                case PLUSPLUS:
                case MINUSMINUS:
                    t = lex.get();
                    expr = org.hotswap.agent.javassist.compiler.ast.Expr.make(t, null, expr);
                    break;
                case '.':
                    lex.get();
                    t = lex.get();
                    if (t == CLASS) {
                        expr = parseDotClass(expr, 0);
                    } else if (t == Identifier) {
                        str = lex.getString();
                        expr = org.hotswap.agent.javassist.compiler.ast.Expr.make('.', expr, new org.hotswap.agent.javassist.compiler.ast.Member(str));
                    } else
                        throw new CompileError("missing member name", lex);
                    break;
                case '#':
                    lex.get();
                    t = lex.get();
                    if (t != Identifier)
                        throw new CompileError("missing static member name", lex);

                    str = lex.getString();
                    expr = org.hotswap.agent.javassist.compiler.ast.Expr.make(MEMBER, new org.hotswap.agent.javassist.compiler.ast.Symbol(toClassName(expr)),
                            new org.hotswap.agent.javassist.compiler.ast.Member(str));
                    break;
                default:
                    return expr;
            }
        }
    }

    /* Parse a .class expression on a class type.  For example,
     * String.class   => ('.' "String" "class")
     * String[].class => ('.' "[LString;" "class")
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseDotClass(org.hotswap.agent.javassist.compiler.ast.ASTree className, int dim)
            throws CompileError {
        String cname = toClassName(className);
        if (dim > 0) {
            StringBuffer sbuf = new StringBuffer();
            while (dim-- > 0)
                sbuf.append('[');

            sbuf.append('L').append(cname.replace('.', '/')).append(';');
            cname = sbuf.toString();
        }

        return org.hotswap.agent.javassist.compiler.ast.Expr.make('.', new org.hotswap.agent.javassist.compiler.ast.Symbol(cname), new org.hotswap.agent.javassist.compiler.ast.Member("class"));
    }

    /* Parses a .class expression on a built-in type.  For example,
     * int.class   => ('#' "java.lang.Integer" "TYPE")
     * int[].class => ('.' "[I", "class")
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseDotClass(int builtinType, int dim)
            throws CompileError {
        if (dim > 0) {
            String cname = CodeGen.toJvmTypeName(builtinType, dim);
            return org.hotswap.agent.javassist.compiler.ast.Expr.make('.', new org.hotswap.agent.javassist.compiler.ast.Symbol(cname), new org.hotswap.agent.javassist.compiler.ast.Member("class"));
        } else {
            String cname;
            switch (builtinType) {
                case BOOLEAN:
                    cname = "java.lang.Boolean";
                    break;
                case BYTE:
                    cname = "java.lang.Byte";
                    break;
                case CHAR:
                    cname = "java.lang.Character";
                    break;
                case SHORT:
                    cname = "java.lang.Short";
                    break;
                case INT:
                    cname = "java.lang.Integer";
                    break;
                case LONG:
                    cname = "java.lang.Long";
                    break;
                case FLOAT:
                    cname = "java.lang.Float";
                    break;
                case DOUBLE:
                    cname = "java.lang.Double";
                    break;
                case VOID:
                    cname = "java.lang.Void";
                    break;
                default:
                    throw new CompileError("invalid builtin type: "
                            + builtinType);
            }

            return org.hotswap.agent.javassist.compiler.ast.Expr.make(MEMBER, new org.hotswap.agent.javassist.compiler.ast.Symbol(cname), new org.hotswap.agent.javassist.compiler.ast.Member("TYPE"));
        }
    }

    /* method.call : method.expr "(" argument.list ")"
     * method.expr : THIS | SUPER | Identifier
     *             | postfix.expr "." Identifier
     *             | postfix.expr "#" Identifier
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseMethodCall(SymbolTable tbl, org.hotswap.agent.javassist.compiler.ast.ASTree expr)
            throws CompileError {
        if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Keyword) {
            int token = ((org.hotswap.agent.javassist.compiler.ast.Keyword) expr).get();
            if (token != THIS && token != SUPER)
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);
        } else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Symbol)        // Identifier
            ;
        else if (expr instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            int op = ((org.hotswap.agent.javassist.compiler.ast.Expr) expr).getOperator();
            if (op != '.' && op != MEMBER)
                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);
        }

        return org.hotswap.agent.javassist.compiler.ast.CallExpr.makeCall(expr, parseArgumentList(tbl));
    }

    private String toClassName(org.hotswap.agent.javassist.compiler.ast.ASTree name)
            throws CompileError {
        StringBuffer sbuf = new StringBuffer();
        toClassName(name, sbuf);
        return sbuf.toString();
    }

    private void toClassName(org.hotswap.agent.javassist.compiler.ast.ASTree name, StringBuffer sbuf)
            throws CompileError {
        if (name instanceof org.hotswap.agent.javassist.compiler.ast.Symbol) {
            sbuf.append(((org.hotswap.agent.javassist.compiler.ast.Symbol) name).get());
            return;
        } else if (name instanceof org.hotswap.agent.javassist.compiler.ast.Expr) {
            org.hotswap.agent.javassist.compiler.ast.Expr expr = (org.hotswap.agent.javassist.compiler.ast.Expr) name;
            if (expr.getOperator() == '.') {
                toClassName(expr.oprand1(), sbuf);
                sbuf.append('.');
                toClassName(expr.oprand2(), sbuf);
                return;
            }
        }

        throw new CompileError("bad static member access", lex);
    }

    /* primary.expr : THIS | SUPER | TRUE | FALSE | NULL
     *              | StringL
     *              | Identifier
     *              | NEW new.expr
     *              | "(" expression ")"
     *              | builtin.type ( "[" "]" )* "." CLASS
     *
     * Identifier represents either a local variable name, a member name,
     * or a class name.
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parsePrimaryExpr(SymbolTable tbl) throws CompileError {
        int t;
        String name;
        org.hotswap.agent.javassist.compiler.ast.Declarator decl;
        org.hotswap.agent.javassist.compiler.ast.ASTree expr;

        switch (t = lex.get()) {
            case THIS:
            case SUPER:
            case TRUE:
            case FALSE:
            case NULL:
                return new org.hotswap.agent.javassist.compiler.ast.Keyword(t);
            case Identifier:
                name = lex.getString();
                decl = tbl.lookup(name);
                if (decl == null)
                    return new org.hotswap.agent.javassist.compiler.ast.Member(name);        // this or static member
                else
                    return new org.hotswap.agent.javassist.compiler.ast.Variable(name, decl); // local variable
            case StringL:
                return new org.hotswap.agent.javassist.compiler.ast.StringL(lex.getString());
            case NEW:
                return parseNew(tbl);
            case '(':
                expr = parseExpression(tbl);
                if (lex.get() == ')')
                    return expr;
                else
                    throw new CompileError(") is missing", lex);
            default:
                if (isBuiltinType(t) || t == VOID) {
                    int dim = parseArrayDimension();
                    if (lex.get() == '.' && lex.get() == CLASS)
                        return parseDotClass(t, dim);
                }

                throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);
        }
    }

    /* new.expr : class.type "(" argument.list ")"
     *          | class.type     array.size [ array.initializer ]
     *          | primitive.type array.size [ array.initializer ]
     */
    private org.hotswap.agent.javassist.compiler.ast.NewExpr parseNew(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ArrayInit init = null;
        int t = lex.lookAhead();
        if (isBuiltinType(t)) {
            lex.get();
            org.hotswap.agent.javassist.compiler.ast.ASTList size = parseArraySize(tbl);
            if (lex.lookAhead() == '{')
                init = parseArrayInitializer(tbl);

            return new org.hotswap.agent.javassist.compiler.ast.NewExpr(t, size, init);
        } else if (t == Identifier) {
            org.hotswap.agent.javassist.compiler.ast.ASTList name = parseClassType(tbl);
            t = lex.lookAhead();
            if (t == '(') {
                org.hotswap.agent.javassist.compiler.ast.ASTList args = parseArgumentList(tbl);
                return new org.hotswap.agent.javassist.compiler.ast.NewExpr(name, args);
            } else if (t == '[') {
                org.hotswap.agent.javassist.compiler.ast.ASTList size = parseArraySize(tbl);
                if (lex.lookAhead() == '{')
                    init = parseArrayInitializer(tbl);

                return org.hotswap.agent.javassist.compiler.ast.NewExpr.makeObjectArray(name, size, init);
            }
        }

        throw new org.hotswap.agent.javassist.compiler.SyntaxError(lex);
    }

    /* array.size : [ array.index ]*
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTList parseArraySize(SymbolTable tbl) throws CompileError {
        org.hotswap.agent.javassist.compiler.ast.ASTList list = null;
        while (lex.lookAhead() == '[')
            list = org.hotswap.agent.javassist.compiler.ast.ASTList.append(list, parseArrayIndex(tbl));

        return list;
    }

    /* array.index : "[" [ expression ] "]"
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTree parseArrayIndex(SymbolTable tbl) throws CompileError {
        lex.get();      // '['
        if (lex.lookAhead() == ']') {
            lex.get();
            return null;
        } else {
            org.hotswap.agent.javassist.compiler.ast.ASTree index = parseExpression(tbl);
            if (lex.get() != ']')
                throw new CompileError("] is missing", lex);

            return index;
        }
    }

    /* argument.list : "(" [ expression [ "," expression ]* ] ")"
     */
    private org.hotswap.agent.javassist.compiler.ast.ASTList parseArgumentList(SymbolTable tbl) throws CompileError {
        if (lex.get() != '(')
            throw new CompileError("( is missing", lex);

        org.hotswap.agent.javassist.compiler.ast.ASTList list = null;
        if (lex.lookAhead() != ')')
            for (; ; ) {
                list = org.hotswap.agent.javassist.compiler.ast.ASTList.append(list, parseExpression(tbl));
                if (lex.lookAhead() == ',')
                    lex.get();
                else
                    break;
            }

        if (lex.get() != ')')
            throw new CompileError(") is missing", lex);

        return list;
    }
}

