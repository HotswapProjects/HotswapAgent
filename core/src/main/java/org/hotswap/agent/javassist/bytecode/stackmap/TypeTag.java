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

package org.hotswap.agent.javassist.bytecode.stackmap;

public interface TypeTag {
    String TOP_TYPE = "*top*";
    org.hotswap.agent.javassist.bytecode.stackmap.TypeData TOP = new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType(TOP_TYPE, org.hotswap.agent.javassist.bytecode.StackMapTable.TOP);
    org.hotswap.agent.javassist.bytecode.stackmap.TypeData INTEGER = new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType("int", org.hotswap.agent.javassist.bytecode.StackMapTable.INTEGER);
    org.hotswap.agent.javassist.bytecode.stackmap.TypeData FLOAT = new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType("float", org.hotswap.agent.javassist.bytecode.StackMapTable.FLOAT);
    org.hotswap.agent.javassist.bytecode.stackmap.TypeData DOUBLE = new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType("double", org.hotswap.agent.javassist.bytecode.StackMapTable.DOUBLE);
    org.hotswap.agent.javassist.bytecode.stackmap.TypeData LONG = new org.hotswap.agent.javassist.bytecode.stackmap.TypeData.BasicType("long", org.hotswap.agent.javassist.bytecode.StackMapTable.LONG);

    // and NULL, THIS, OBJECT, UNINIT
}
