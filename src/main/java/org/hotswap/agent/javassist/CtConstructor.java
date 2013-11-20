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

package org.hotswap.agent.javassist;

import org.hotswap.agent.javassist.compiler.CompileError;
import org.hotswap.agent.javassist.compiler.Javac;

/**
 * An instance of CtConstructor represents a constructor.
 * It may represent a static constructor
 * (class initializer).  To distinguish a constructor and a class
 * initializer, call <code>isClassInitializer()</code>.
 * <p/>
 * <p>See the super class <code>CtBehavior</code> as well since
 * a number of useful methods are in <code>CtBehavior</code>.
 *
 * @see org.hotswap.agent.javassist.CtClass#getDeclaredConstructors()
 * @see org.hotswap.agent.javassist.CtClass#getClassInitializer()
 * @see CtNewConstructor
 */
public final class CtConstructor extends CtBehavior {
    protected CtConstructor(org.hotswap.agent.javassist.bytecode.MethodInfo minfo, org.hotswap.agent.javassist.CtClass declaring) {
        super(declaring, minfo);
    }

    /**
     * Creates a constructor with no constructor body.
     * The created constructor
     * must be added to a class with <code>CtClass.addConstructor()</code>.
     * <p/>
     * <p>The created constructor does not include a constructor body,
     * which must be specified with <code>setBody()</code>.
     *
     * @param declaring  the class to which the created method is added.
     * @param parameters a list of the parameter types
     * @see org.hotswap.agent.javassist.CtClass#addConstructor(CtConstructor)
     * @see CtConstructor#setBody(String)
     * @see CtConstructor#setBody(CtConstructor, org.hotswap.agent.javassist.ClassMap)
     */
    public CtConstructor(org.hotswap.agent.javassist.CtClass[] parameters, org.hotswap.agent.javassist.CtClass declaring) {
        this((org.hotswap.agent.javassist.bytecode.MethodInfo) null, declaring);
        org.hotswap.agent.javassist.bytecode.ConstPool cp = declaring.getClassFile2().getConstPool();
        String desc = org.hotswap.agent.javassist.bytecode.Descriptor.ofConstructor(parameters);
        methodInfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, "<init>", desc);
        setModifiers(org.hotswap.agent.javassist.Modifier.PUBLIC);
    }

    /**
     * Creates a copy of a <code>CtConstructor</code> object.
     * The created constructor must be
     * added to a class with <code>CtClass.addConstructor()</code>.
     * <p/>
     * <p>All occurrences of class names in the created constructor
     * are replaced with names specified by
     * <code>map</code> if <code>map</code> is not <code>null</code>.
     * <p/>
     * <p>By default, all the occurrences of the names of the class
     * declaring <code>src</code> and the superclass are replaced
     * with the name of the class and the superclass that
     * the created constructor is added to.
     * This is done whichever <code>map</code> is null or not.
     * To prevent this replacement, call <code>ClassMap.fix()</code>
     * or <code>put()</code> to explicitly specify replacement.
     * <p/>
     * <p><b>Note:</b> if the <code>.class</code> notation (for example,
     * <code>String.class</code>) is included in an expression, the
     * Javac compiler may produce a helper method.
     * Since this constructor never
     * copies this helper method, the programmers have the responsiblity of
     * copying it.  Otherwise, use <code>Class.forName()</code> in the
     * expression.
     *
     * @param src       the source method.
     * @param declaring the class to which the created method is added.
     * @param map       the hashtable associating original class names
     *                  with substituted names.
     *                  It can be <code>null</code>.
     * @see org.hotswap.agent.javassist.CtClass#addConstructor(CtConstructor)
     * @see org.hotswap.agent.javassist.ClassMap#fix(String)
     */
    public CtConstructor(CtConstructor src, org.hotswap.agent.javassist.CtClass declaring, org.hotswap.agent.javassist.ClassMap map)
            throws org.hotswap.agent.javassist.CannotCompileException {
        this((org.hotswap.agent.javassist.bytecode.MethodInfo) null, declaring);
        copy(src, true, map);
    }

    /**
     * Returns true if this object represents a constructor.
     */
    public boolean isConstructor() {
        return methodInfo.isConstructor();
    }

    /**
     * Returns true if this object represents a static initializer.
     */
    public boolean isClassInitializer() {
        return methodInfo.isStaticInitializer();
    }

    /**
     * Returns the constructor name followed by parameter types
     * such as <code>CtConstructor(CtClass[],CtClass)</code>.
     *
     * @since 3.5
     */
    public String getLongName() {
        return getDeclaringClass().getName()
                + (isConstructor() ? org.hotswap.agent.javassist.bytecode.Descriptor.toString(getSignature())
                : ("." + org.hotswap.agent.javassist.bytecode.MethodInfo.nameClinit + "()"));
    }

    /**
     * Obtains the name of this constructor.
     * It is the same as the simple name of the class declaring this
     * constructor.  If this object represents a class initializer,
     * then this method returns <code>"&lt;clinit&gt;"</code>.
     */
    public String getName() {
        if (methodInfo.isStaticInitializer())
            return org.hotswap.agent.javassist.bytecode.MethodInfo.nameClinit;
        else
            return declaringClass.getSimpleName();
    }

    /**
     * Returns true if the constructor (or static initializer)
     * is the default one.  This method returns true if the constructor
     * takes some arguments but it does not perform anything except
     * calling <code>super()</code> (the no-argument constructor of
     * the super class).
     */
    public boolean isEmpty() {
        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = getMethodInfo2().getCodeAttribute();
        if (ca == null)
            return false;       // native or abstract??
        // they are not allowed, though.

        org.hotswap.agent.javassist.bytecode.ConstPool cp = ca.getConstPool();
        org.hotswap.agent.javassist.bytecode.CodeIterator it = ca.iterator();
        try {
            int pos, desc;
            int op0 = it.byteAt(it.next());
            return op0 == org.hotswap.agent.javassist.bytecode.Opcode.RETURN     // empty static initializer
                    || (op0 == org.hotswap.agent.javassist.bytecode.Opcode.ALOAD_0
                    && it.byteAt(pos = it.next()) == org.hotswap.agent.javassist.bytecode.Opcode.INVOKESPECIAL
                    && (desc = cp.isConstructor(getSuperclassName(),
                    it.u16bitAt(pos + 1))) != 0
                    && "()V".equals(cp.getUtf8Info(desc))
                    && it.byteAt(it.next()) == org.hotswap.agent.javassist.bytecode.Opcode.RETURN
                    && !it.hasNext());
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
        }
        return false;
    }

    private String getSuperclassName() {
        org.hotswap.agent.javassist.bytecode.ClassFile cf = declaringClass.getClassFile2();
        return cf.getSuperclass();
    }

    /**
     * Returns true if this constructor calls a constructor
     * of the super class.  This method returns false if it
     * calls another constructor of this class by <code>this()</code>.
     */
    public boolean callsSuper() throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.CodeAttribute codeAttr = methodInfo.getCodeAttribute();
        if (codeAttr != null) {
            org.hotswap.agent.javassist.bytecode.CodeIterator it = codeAttr.iterator();
            try {
                int index = it.skipSuperConstructor();
                return index >= 0;
            } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
                throw new org.hotswap.agent.javassist.CannotCompileException(e);
            }
        }

        return false;
    }

    /**
     * Sets a constructor body.
     *
     * @param src the source code representing the constructor body.
     *            It must be a single statement or block.
     *            If it is <code>null</code>, the substituted
     *            constructor body does nothing except calling
     *            <code>super()</code>.
     */
    public void setBody(String src) throws org.hotswap.agent.javassist.CannotCompileException {
        if (src == null)
            if (isClassInitializer())
                src = ";";
            else
                src = "super();";

        super.setBody(src);
    }

    /**
     * Copies a constructor body from another constructor.
     * <p/>
     * <p>All occurrences of the class names in the copied body
     * are replaced with the names specified by
     * <code>map</code> if <code>map</code> is not <code>null</code>.
     *
     * @param src the method that the body is copied from.
     * @param map the hashtable associating original class names
     *            with substituted names.
     *            It can be <code>null</code>.
     */
    public void setBody(CtConstructor src, org.hotswap.agent.javassist.ClassMap map)
            throws org.hotswap.agent.javassist.CannotCompileException {
        setBody0(src.declaringClass, src.methodInfo,
                declaringClass, methodInfo, map);
    }

    /**
     * Inserts bytecode just after another constructor in the super class
     * or this class is called.
     * It does not work if this object represents a class initializer.
     *
     * @param src the source code representing the inserted bytecode.
     *            It must be a single statement or block.
     */
    public void insertBeforeBody(String src) throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.CtClass cc = declaringClass;
        cc.checkModify();
        if (isClassInitializer())
            throw new org.hotswap.agent.javassist.CannotCompileException("class initializer");

        org.hotswap.agent.javassist.bytecode.CodeAttribute ca = methodInfo.getCodeAttribute();
        org.hotswap.agent.javassist.bytecode.CodeIterator iterator = ca.iterator();
        org.hotswap.agent.javassist.bytecode.Bytecode b = new org.hotswap.agent.javassist.bytecode.Bytecode(methodInfo.getConstPool(),
                ca.getMaxStack(), ca.getMaxLocals());
        b.setStackDepth(ca.getMaxStack());
        Javac jv = new Javac(b, cc);
        try {
            jv.recordParams(getParameterTypes(), false);
            jv.compileStmnt(src);
            ca.setMaxStack(b.getMaxStack());
            ca.setMaxLocals(b.getMaxLocals());
            iterator.skipConstructor();
            int pos = iterator.insertEx(b.get());
            iterator.insert(b.getExceptionTable(), pos);
            methodInfo.rebuildStackMapIf6(cc.getClassPool(), cc.getClassFile2());
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    /* This method is called by addCatch() in CtBehavior.
     * super() and this() must not be in a try statement.
     */
    int getStartPosOfBody(org.hotswap.agent.javassist.bytecode.CodeAttribute ca) throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.CodeIterator ci = ca.iterator();
        try {
            ci.skipConstructor();
            return ci.next();
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    /**
     * Makes a copy of this constructor and converts it into a method.
     * The signature of the mehtod is the same as the that of this constructor.
     * The return type is <code>void</code>.  The resulting method must be
     * appended to the class specified by <code>declaring</code>.
     * If this constructor is a static initializer, the resulting method takes
     * no parameter.
     * <p/>
     * <p>An occurrence of another constructor call <code>this()</code>
     * or a super constructor call <code>super()</code> is
     * eliminated from the resulting method.
     * <p/>
     * <p>The immediate super class of the class declaring this constructor
     * must be also a super class of the class declaring the resulting method.
     * If the constructor accesses a field, the class declaring the resulting method
     * must also declare a field with the same name and type.
     *
     * @param name      the name of the resulting method.
     * @param declaring the class declaring the resulting method.
     */
    public org.hotswap.agent.javassist.CtMethod toMethod(String name, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        return toMethod(name, declaring, null);
    }

    /**
     * Makes a copy of this constructor and converts it into a method.
     * The signature of the method is the same as the that of this constructor.
     * The return type is <code>void</code>.  The resulting method must be
     * appended to the class specified by <code>declaring</code>.
     * If this constructor is a static initializer, the resulting method takes
     * no parameter.
     * <p/>
     * <p>An occurrence of another constructor call <code>this()</code>
     * or a super constructor call <code>super()</code> is
     * eliminated from the resulting method.
     * <p/>
     * <p>The immediate super class of the class declaring this constructor
     * must be also a super class of the class declaring the resulting method
     * (this is obviously true if the second parameter <code>declaring</code> is
     * the same as the class declaring this constructor).
     * If the constructor accesses a field, the class declaring the resulting method
     * must also declare a field with the same name and type.
     *
     * @param name      the name of the resulting method.
     * @param declaring the class declaring the resulting method.
     *                  It is normally the same as the class declaring this
     *                  constructor.
     * @param map       the hash table associating original class names
     *                  with substituted names.  The original class names will be
     *                  replaced while making a copy.
     *                  <code>map</code> can be <code>null</code>.
     */
    public org.hotswap.agent.javassist.CtMethod toMethod(String name, org.hotswap.agent.javassist.CtClass declaring, org.hotswap.agent.javassist.ClassMap map)
            throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.CtMethod method = new org.hotswap.agent.javassist.CtMethod(null, declaring);
        method.copy(this, false, map);
        if (isConstructor()) {
            org.hotswap.agent.javassist.bytecode.MethodInfo minfo = method.getMethodInfo2();
            org.hotswap.agent.javassist.bytecode.CodeAttribute ca = minfo.getCodeAttribute();
            if (ca != null) {
                removeConsCall(ca);
                try {
                    methodInfo.rebuildStackMapIf6(declaring.getClassPool(),
                            declaring.getClassFile2());
                } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
                    throw new org.hotswap.agent.javassist.CannotCompileException(e);
                }
            }
        }

        method.setName(name);
        return method;
    }

    private static void removeConsCall(org.hotswap.agent.javassist.bytecode.CodeAttribute ca)
            throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.CodeIterator iterator = ca.iterator();
        try {
            int pos = iterator.skipConstructor();
            if (pos >= 0) {
                int mref = iterator.u16bitAt(pos + 1);
                String desc = ca.getConstPool().getMethodrefType(mref);
                int num = org.hotswap.agent.javassist.bytecode.Descriptor.numOfParameters(desc) + 1;
                if (num > 3)
                    pos = iterator.insertGapAt(pos, num - 3, false).position;

                iterator.writeByte(org.hotswap.agent.javassist.bytecode.Opcode.POP, pos++);  // this
                iterator.writeByte(org.hotswap.agent.javassist.bytecode.Opcode.NOP, pos);
                iterator.writeByte(org.hotswap.agent.javassist.bytecode.Opcode.NOP, pos + 1);
                org.hotswap.agent.javassist.bytecode.Descriptor.Iterator it = new org.hotswap.agent.javassist.bytecode.Descriptor.Iterator(desc);
                while (true) {
                    it.next();
                    if (it.isParameter())
                        iterator.writeByte(it.is2byte() ? org.hotswap.agent.javassist.bytecode.Opcode.POP2 : org.hotswap.agent.javassist.bytecode.Opcode.POP,
                                pos++);
                    else
                        break;
                }
            }
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }
}
