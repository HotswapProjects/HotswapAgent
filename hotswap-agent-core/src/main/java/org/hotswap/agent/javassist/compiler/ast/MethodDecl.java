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

public class MethodDecl extends org.hotswap.agent.javassist.compiler.ast.ASTList {
    public static final String initName = "<init>";

    public MethodDecl(ASTree _head, org.hotswap.agent.javassist.compiler.ast.ASTList _tail) {
        super(_head, _tail);
    }

    public boolean isConstructor() {
        Symbol sym = getReturn().getVariable();
        return sym != null && initName.equals(sym.get());
    }

    public org.hotswap.agent.javassist.compiler.ast.ASTList getModifiers() {
        return (org.hotswap.agent.javassist.compiler.ast.ASTList) getLeft();
    }

    public org.hotswap.agent.javassist.compiler.ast.Declarator getReturn() {
        return (org.hotswap.agent.javassist.compiler.ast.Declarator) tail().head();
    }

    public org.hotswap.agent.javassist.compiler.ast.ASTList getParams() {
        return (org.hotswap.agent.javassist.compiler.ast.ASTList) sublist(2).head();
    }

    public org.hotswap.agent.javassist.compiler.ast.ASTList getThrows() {
        return (org.hotswap.agent.javassist.compiler.ast.ASTList) sublist(3).head();
    }

    public Stmnt getBody() {
        return (Stmnt) sublist(4).head();
    }

    public void accept(Visitor v) throws org.hotswap.agent.javassist.compiler.CompileError {
        v.atMethodDecl(this);
    }
}
