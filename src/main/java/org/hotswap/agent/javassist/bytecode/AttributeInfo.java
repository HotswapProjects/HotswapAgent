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
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

// Note: if you define a new subclass of AttributeInfo, then
//       update AttributeInfo.read(), .copy(), and (maybe) write().

/**
 * <code>attribute_info</code> structure.
 */
public class AttributeInfo {
    protected org.hotswap.agent.javassist.bytecode.ConstPool constPool;
    int name;
    byte[] info;

    protected AttributeInfo(org.hotswap.agent.javassist.bytecode.ConstPool cp, int attrname, byte[] attrinfo) {
        constPool = cp;
        name = attrname;
        info = attrinfo;
    }

    protected AttributeInfo(org.hotswap.agent.javassist.bytecode.ConstPool cp, String attrname) {
        this(cp, attrname, (byte[]) null);
    }

    /**
     * Constructs an <code>attribute_info</code> structure.
     *
     * @param cp       constant pool table
     * @param attrname attribute name
     * @param attrinfo <code>info</code> field
     *                 of <code>attribute_info</code> structure.
     */
    public AttributeInfo(org.hotswap.agent.javassist.bytecode.ConstPool cp, String attrname, byte[] attrinfo) {
        this(cp, cp.addUtf8Info(attrname), attrinfo);
    }

    protected AttributeInfo(org.hotswap.agent.javassist.bytecode.ConstPool cp, int n, DataInputStream in)
            throws IOException {
        constPool = cp;
        name = n;
        int len = in.readInt();
        info = new byte[len];
        if (len > 0)
            in.readFully(info);
    }

    static AttributeInfo read(org.hotswap.agent.javassist.bytecode.ConstPool cp, DataInputStream in)
            throws IOException {
        int name = in.readUnsignedShort();
        String nameStr = cp.getUtf8Info(name);
        if (nameStr.charAt(0) < 'L') {
            if (nameStr.equals(AnnotationDefaultAttribute.tag))
                return new AnnotationDefaultAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.BootstrapMethodsAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.BootstrapMethodsAttribute(cp, name, in);
            else if (nameStr.equals(CodeAttribute.tag))
                return new CodeAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.ConstantAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.ConstantAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.DeprecatedAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.DeprecatedAttribute(cp, name, in);
            else if (nameStr.equals(EnclosingMethodAttribute.tag))
                return new EnclosingMethodAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.ExceptionsAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.ExceptionsAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.InnerClassesAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.InnerClassesAttribute(cp, name, in);
        } else {
            /* Note that the names of Annotations attributes begin with 'R'. 
             */
            if (nameStr.equals(org.hotswap.agent.javassist.bytecode.LineNumberAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.LineNumberAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.LocalVariableAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.LocalVariableAttribute(cp, name, in);
            else if (nameStr.equals(LocalVariableTypeAttribute.tag))
                return new LocalVariableTypeAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.AnnotationsAttribute.visibleTag)
                    || nameStr.equals(org.hotswap.agent.javassist.bytecode.AnnotationsAttribute.invisibleTag)) {
                // RuntimeVisibleAnnotations or RuntimeInvisibleAnnotations
                return new org.hotswap.agent.javassist.bytecode.AnnotationsAttribute(cp, name, in);
            } else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.ParameterAnnotationsAttribute.visibleTag)
                    || nameStr.equals(org.hotswap.agent.javassist.bytecode.ParameterAnnotationsAttribute.invisibleTag))
                return new org.hotswap.agent.javassist.bytecode.ParameterAnnotationsAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.SignatureAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.SignatureAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.SourceFileAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.SourceFileAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.SyntheticAttribute.tag))
                return new org.hotswap.agent.javassist.bytecode.SyntheticAttribute(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.StackMap.tag))
                return new org.hotswap.agent.javassist.bytecode.StackMap(cp, name, in);
            else if (nameStr.equals(org.hotswap.agent.javassist.bytecode.StackMapTable.tag))
                return new org.hotswap.agent.javassist.bytecode.StackMapTable(cp, name, in);
        }

        return new AttributeInfo(cp, name, in);
    }

    /**
     * Returns an attribute name.
     */
    public String getName() {
        return constPool.getUtf8Info(name);
    }

    /**
     * Returns a constant pool table.
     */
    public org.hotswap.agent.javassist.bytecode.ConstPool getConstPool() {
        return constPool;
    }

    /**
     * Returns the length of this <code>attribute_info</code>
     * structure.
     * The returned value is <code>attribute_length + 6</code>.
     */
    public int length() {
        return info.length + 6;
    }

    /**
     * Returns the <code>info</code> field
     * of this <code>attribute_info</code> structure.
     * <p/>
     * <p>This method is not available if the object is an instance
     * of <code>CodeAttribute</code>.
     */
    public byte[] get() {
        return info;
    }

    /**
     * Sets the <code>info</code> field
     * of this <code>attribute_info</code> structure.
     * <p/>
     * <p>This method is not available if the object is an instance
     * of <code>CodeAttribute</code>.
     */
    public void set(byte[] newinfo) {
        info = newinfo;
    }

    /**
     * Makes a copy.  Class names are replaced according to the
     * given <code>Map</code> object.
     *
     * @param newCp      the constant pool table used by the new copy.
     * @param classnames pairs of replaced and substituted
     *                   class names.
     */
    public AttributeInfo copy(org.hotswap.agent.javassist.bytecode.ConstPool newCp, Map classnames) {
        int s = info.length;
        byte[] srcInfo = info;
        byte[] newInfo = new byte[s];
        for (int i = 0; i < s; ++i)
            newInfo[i] = srcInfo[i];

        return new AttributeInfo(newCp, getName(), newInfo);
    }

    void write(DataOutputStream out) throws IOException {
        out.writeShort(name);
        out.writeInt(info.length);
        if (info.length > 0)
            out.write(info);
    }

    static int getLength(ArrayList list) {
        int size = 0;
        int n = list.size();
        for (int i = 0; i < n; ++i) {
            AttributeInfo attr = (AttributeInfo) list.get(i);
            size += attr.length();
        }

        return size;
    }

    static AttributeInfo lookup(ArrayList list, String name) {
        if (list == null)
            return null;

        ListIterator iterator = list.listIterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo) iterator.next();
            if (ai.getName().equals(name))
                return ai;
        }

        return null;            // no such attribute
    }

    static synchronized void remove(ArrayList list, String name) {
        if (list == null)
            return;

        ListIterator iterator = list.listIterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo) iterator.next();
            if (ai.getName().equals(name))
                iterator.remove();
        }
    }

    static void writeAll(ArrayList list, DataOutputStream out)
            throws IOException {
        if (list == null)
            return;

        int n = list.size();
        for (int i = 0; i < n; ++i) {
            AttributeInfo attr = (AttributeInfo) list.get(i);
            attr.write(out);
        }
    }

    static ArrayList copyAll(ArrayList list, org.hotswap.agent.javassist.bytecode.ConstPool cp) {
        if (list == null)
            return null;

        ArrayList newList = new ArrayList();
        int n = list.size();
        for (int i = 0; i < n; ++i) {
            AttributeInfo attr = (AttributeInfo) list.get(i);
            newList.add(attr.copy(cp, null));
        }

        return newList;
    }

    /* The following two methods are used to implement
     * ClassFile.renameClass().
     * Only CodeAttribute, LocalVariableAttribute,
     * AnnotationsAttribute, and SignatureAttribute
     * override these methods.
     */
    void renameClass(String oldname, String newname) {
    }

    void renameClass(Map classnames) {
    }

    static void renameClass(List attributes, String oldname, String newname) {
        Iterator iterator = attributes.iterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo) iterator.next();
            ai.renameClass(oldname, newname);
        }
    }

    static void renameClass(List attributes, Map classnames) {
        Iterator iterator = attributes.iterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo) iterator.next();
            ai.renameClass(classnames);
        }
    }

    void getRefClasses(Map classnames) {
    }

    static void getRefClasses(List attributes, Map classnames) {
        Iterator iterator = attributes.iterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo) iterator.next();
            ai.getRefClasses(classnames);
        }
    }
}
