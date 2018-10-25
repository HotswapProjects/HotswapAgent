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
import org.hotswap.agent.javassist.CtField;

/**
 * Member name.
 */
public class Member extends Symbol {
    // cache maintained by fieldAccess() in TypeChecker.
    // this is used to obtain the value of a static final field.
    private CtField field;

    public Member(String name) {
        super(name);
        field = null;
    }

    public void setField(CtField f) { field = f; }

    public CtField getField() { return field; }

    public void accept(Visitor v) throws CompileError { v.atMember(this); }
}
