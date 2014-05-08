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

package org.hotswap.agent.javassist.compiler.ast;

import org.hotswap.agent.javassist.compiler.CompileError;

/**
 * The visitor pattern.
 *
 * @see ast.ASTree#accept(Visitor)
 */
public class Visitor {
    public void atASTList(ASTList n) throws CompileError {
    }

    public void atPair(org.hotswap.agent.javassist.compiler.ast.Pair n) throws CompileError {
    }

    public void atFieldDecl(org.hotswap.agent.javassist.compiler.ast.FieldDecl n) throws CompileError {
    }

    public void atMethodDecl(org.hotswap.agent.javassist.compiler.ast.MethodDecl n) throws CompileError {
    }

    public void atStmnt(org.hotswap.agent.javassist.compiler.ast.Stmnt n) throws CompileError {
    }

    public void atDeclarator(org.hotswap.agent.javassist.compiler.ast.Declarator n) throws CompileError {
    }

    public void atAssignExpr(org.hotswap.agent.javassist.compiler.ast.AssignExpr n) throws CompileError {
    }

    public void atCondExpr(org.hotswap.agent.javassist.compiler.ast.CondExpr n) throws CompileError {
    }

    public void atBinExpr(org.hotswap.agent.javassist.compiler.ast.BinExpr n) throws CompileError {
    }

    public void atExpr(org.hotswap.agent.javassist.compiler.ast.Expr n) throws CompileError {
    }

    public void atCallExpr(org.hotswap.agent.javassist.compiler.ast.CallExpr n) throws CompileError {
    }

    public void atCastExpr(org.hotswap.agent.javassist.compiler.ast.CastExpr n) throws CompileError {
    }

    public void atInstanceOfExpr(org.hotswap.agent.javassist.compiler.ast.InstanceOfExpr n) throws CompileError {
    }

    public void atNewExpr(org.hotswap.agent.javassist.compiler.ast.NewExpr n) throws CompileError {
    }

    public void atSymbol(org.hotswap.agent.javassist.compiler.ast.Symbol n) throws CompileError {
    }

    public void atMember(org.hotswap.agent.javassist.compiler.ast.Member n) throws CompileError {
    }

    public void atVariable(org.hotswap.agent.javassist.compiler.ast.Variable n) throws CompileError {
    }

    public void atKeyword(org.hotswap.agent.javassist.compiler.ast.Keyword n) throws CompileError {
    }

    public void atStringL(org.hotswap.agent.javassist.compiler.ast.StringL n) throws CompileError {
    }

    public void atIntConst(org.hotswap.agent.javassist.compiler.ast.IntConst n) throws CompileError {
    }

    public void atDoubleConst(org.hotswap.agent.javassist.compiler.ast.DoubleConst n) throws CompileError {
    }

    public void atArrayInit(org.hotswap.agent.javassist.compiler.ast.ArrayInit n) throws CompileError {
    }
}
