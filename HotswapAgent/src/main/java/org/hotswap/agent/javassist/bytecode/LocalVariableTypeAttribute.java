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

package org.hotswap.agent.javassist.bytecode;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * <code>LocalVariableTypeTable_attribute</code>.
 *
 * @since 3.11
 */
public class LocalVariableTypeAttribute extends org.hotswap.agent.javassist.bytecode.LocalVariableAttribute {
    /**
     * The name of the attribute <code>"LocalVariableTypeTable"</code>.
     */
    public static final String tag = org.hotswap.agent.javassist.bytecode.LocalVariableAttribute.typeTag;

    /**
     * Constructs an empty LocalVariableTypeTable.
     */
    public LocalVariableTypeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
        super(cp, tag, new byte[2]);
        ByteArray.write16bit(0, info, 0);
    }

    LocalVariableTypeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp, int n, DataInputStream in)
            throws IOException {
        super(cp, n, in);
    }

    private LocalVariableTypeAttribute(org.hotswap.agent.javassist.bytecode.ConstPool cp, byte[] dest) {
        super(cp, tag, dest);
    }

    String renameEntry(String desc, String oldname, String newname) {
        return org.hotswap.agent.javassist.bytecode.SignatureAttribute.renameClass(desc, oldname, newname);
    }

    String renameEntry(String desc, Map classnames) {
        return org.hotswap.agent.javassist.bytecode.SignatureAttribute.renameClass(desc, classnames);
    }

    org.hotswap.agent.javassist.bytecode.LocalVariableAttribute makeThisAttr(org.hotswap.agent.javassist.bytecode.ConstPool cp, byte[] dest) {
        return new LocalVariableTypeAttribute(cp, dest);
    }
}
