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

import org.hotswap.agent.javassist.CtMethod.ConstParameter;
import org.hotswap.agent.javassist.compiler.CompileError;
import org.hotswap.agent.javassist.compiler.Javac;

/**
 * A collection of static methods for creating a <code>CtMethod</code>.
 * An instance of this class does not make any sense.
 *
 * @see org.hotswap.agent.javassist.CtClass#addMethod(org.hotswap.agent.javassist.CtMethod)
 */
public class CtNewMethod {

    /**
     * Compiles the given source code and creates a method.
     * The source code must include not only the method body
     * but the whole declaration, for example,
     * <p/>
     * <ul><pre>"public Object id(Object obj) { return obj; }"</pre></ul>
     *
     * @param src       the source text.
     * @param declaring the class to which the created method is added.
     */
    public static org.hotswap.agent.javassist.CtMethod make(String src, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        return make(src, declaring, null, null);
    }

    /**
     * Compiles the given source code and creates a method.
     * The source code must include not only the method body
     * but the whole declaration, for example,
     * <p/>
     * <ul><pre>"public Object id(Object obj) { return obj; }"</pre></ul>
     * <p/>
     * <p>If the source code includes <code>$proceed()</code>, then
     * it is compiled into a method call on the specified object.
     *
     * @param src            the source text.
     * @param declaring      the class to which the created method is added.
     * @param delegateObj    the source text specifying the object
     *                       that is called on by <code>$proceed()</code>.
     * @param delegateMethod the name of the method
     *                       that is called by <code>$proceed()</code>.
     */
    public static org.hotswap.agent.javassist.CtMethod make(String src, org.hotswap.agent.javassist.CtClass declaring,
                                                            String delegateObj, String delegateMethod)
            throws org.hotswap.agent.javassist.CannotCompileException {
        Javac compiler = new Javac(declaring);
        try {
            if (delegateMethod != null)
                compiler.recordProceed(delegateObj, delegateMethod);

            CtMember obj = compiler.compile(src);
            if (obj instanceof org.hotswap.agent.javassist.CtMethod)
                return (org.hotswap.agent.javassist.CtMethod) obj;
        } catch (CompileError e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }

        throw new org.hotswap.agent.javassist.CannotCompileException("not a method");
    }

    /**
     * Creates a public (non-static) method.  The created method cannot
     * be changed to a static method later.
     *
     * @param returnType the type of the returned value.
     * @param mname      the method name.
     * @param parameters a list of the parameter types.
     * @param exceptions a list of the exception types.
     * @param body       the source text of the method body.
     *                   It must be a block surrounded by <code>{}</code>.
     *                   If it is <code>null</code>, the created method
     *                   does nothing except returning zero or null.
     * @param declaring  the class to which the created method is added.
     * @see #make(int, org.hotswap.agent.javassist.CtClass, String, org.hotswap.agent.javassist.CtClass[], org.hotswap.agent.javassist.CtClass[], String, org.hotswap.agent.javassist.CtClass)
     */
    public static org.hotswap.agent.javassist.CtMethod make(org.hotswap.agent.javassist.CtClass returnType,
                                                            String mname, org.hotswap.agent.javassist.CtClass[] parameters,
                                                            org.hotswap.agent.javassist.CtClass[] exceptions,
                                                            String body, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        return make(org.hotswap.agent.javassist.Modifier.PUBLIC, returnType, mname, parameters, exceptions,
                body, declaring);
    }

    /**
     * Creates a method.  <code>modifiers</code> can contain
     * <code>Modifier.STATIC</code>.
     *
     * @param modifiers  access modifiers.
     * @param returnType the type of the returned value.
     * @param mname      the method name.
     * @param parameters a list of the parameter types.
     * @param exceptions a list of the exception types.
     * @param body       the source text of the method body.
     *                   It must be a block surrounded by <code>{}</code>.
     *                   If it is <code>null</code>, the created method
     *                   does nothing except returning zero or null.
     * @param declaring  the class to which the created method is added.
     * @see org.hotswap.agent.javassist.Modifier
     */
    public static org.hotswap.agent.javassist.CtMethod make(int modifiers, org.hotswap.agent.javassist.CtClass returnType,
                                                            String mname, org.hotswap.agent.javassist.CtClass[] parameters,
                                                            org.hotswap.agent.javassist.CtClass[] exceptions,
                                                            String body, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            org.hotswap.agent.javassist.CtMethod cm
                    = new org.hotswap.agent.javassist.CtMethod(returnType, mname, parameters, declaring);
            cm.setModifiers(modifiers);
            cm.setExceptionTypes(exceptions);
            cm.setBody(body);
            return cm;
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    /**
     * Creates a copy of a method.  This method is provided for creating
     * a new method based on an existing method.
     * This is a convenience method for calling
     * {@link org.hotswap.agent.javassist.CtMethod#CtMethod(org.hotswap.agent.javassist.CtMethod, org.hotswap.agent.javassist.CtClass, org.hotswap.agent.javassist.ClassMap) this constructor}.
     * See the description of the constructor for particular behavior of the copying.
     *
     * @param src       the source method.
     * @param declaring the class to which the created method is added.
     * @param map       the hash table associating original class names
     *                  with substituted names.
     *                  It can be <code>null</code>.
     * @see org.hotswap.agent.javassist.CtMethod#CtMethod(org.hotswap.agent.javassist.CtMethod, org.hotswap.agent.javassist.CtClass, org.hotswap.agent.javassist.ClassMap)
     */
    public static org.hotswap.agent.javassist.CtMethod copy(org.hotswap.agent.javassist.CtMethod src, org.hotswap.agent.javassist.CtClass declaring,
                                                            org.hotswap.agent.javassist.ClassMap map) throws org.hotswap.agent.javassist.CannotCompileException {
        return new org.hotswap.agent.javassist.CtMethod(src, declaring, map);
    }

    /**
     * Creates a copy of a method with a new name.
     * This method is provided for creating
     * a new method based on an existing method.
     * This is a convenience method for calling
     * {@link org.hotswap.agent.javassist.CtMethod#CtMethod(org.hotswap.agent.javassist.CtMethod, org.hotswap.agent.javassist.CtClass, org.hotswap.agent.javassist.ClassMap) this constructor}.
     * See the description of the constructor for particular behavior of the copying.
     *
     * @param src       the source method.
     * @param name      the name of the created method.
     * @param declaring the class to which the created method is added.
     * @param map       the hash table associating original class names
     *                  with substituted names.
     *                  It can be <code>null</code>.
     * @see org.hotswap.agent.javassist.CtMethod#CtMethod(org.hotswap.agent.javassist.CtMethod, org.hotswap.agent.javassist.CtClass, org.hotswap.agent.javassist.ClassMap)
     */
    public static org.hotswap.agent.javassist.CtMethod copy(org.hotswap.agent.javassist.CtMethod src, String name, org.hotswap.agent.javassist.CtClass declaring,
                                                            org.hotswap.agent.javassist.ClassMap map) throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.CtMethod cm = new org.hotswap.agent.javassist.CtMethod(src, declaring, map);
        cm.setName(name);
        return cm;
    }

    /**
     * Creates a public abstract method.
     *
     * @param returnType the type of the returned value
     * @param mname      the method name
     * @param parameters a list of the parameter types
     * @param exceptions a list of the exception types
     * @param declaring  the class to which the created method is added.
     * @see org.hotswap.agent.javassist.CtMethod#CtMethod(org.hotswap.agent.javassist.CtClass, String, org.hotswap.agent.javassist.CtClass[], org.hotswap.agent.javassist.CtClass)
     */
    public static org.hotswap.agent.javassist.CtMethod abstractMethod(org.hotswap.agent.javassist.CtClass returnType,
                                                                      String mname,
                                                                      org.hotswap.agent.javassist.CtClass[] parameters,
                                                                      org.hotswap.agent.javassist.CtClass[] exceptions,
                                                                      org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtMethod cm = new org.hotswap.agent.javassist.CtMethod(returnType, mname, parameters, declaring);
        cm.setExceptionTypes(exceptions);
        return cm;
    }

    /**
     * Creates a public getter method.  The getter method returns the value
     * of the specified field in the class to which this method is added.
     * The created method is initially not static even if the field is
     * static.  Change the modifiers if the method should be static.
     *
     * @param methodName the name of the getter
     * @param field      the field accessed.
     */
    public static org.hotswap.agent.javassist.CtMethod getter(String methodName, org.hotswap.agent.javassist.CtField field)
            throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.FieldInfo finfo = field.getFieldInfo2();
        String fieldType = finfo.getDescriptor();
        String desc = "()" + fieldType;
        org.hotswap.agent.javassist.bytecode.ConstPool cp = finfo.getConstPool();
        org.hotswap.agent.javassist.bytecode.MethodInfo minfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, methodName, desc);
        minfo.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.PUBLIC);

        org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp, 2, 1);
        try {
            String fieldName = finfo.getName();
            if ((finfo.getAccessFlags() & org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC) == 0) {
                code.addAload(0);
                code.addGetfield(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            } else
                code.addGetstatic(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);

            code.addReturn(field.getType());
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }

        minfo.setCodeAttribute(code.toCodeAttribute());
        org.hotswap.agent.javassist.CtClass cc = field.getDeclaringClass();
        // a stack map is not needed.
        return new org.hotswap.agent.javassist.CtMethod(minfo, cc);
    }

    /**
     * Creates a public setter method.  The setter method assigns the
     * value of the first parameter to the specified field
     * in the class to which this method is added.
     * The created method is not static even if the field is
     * static.  You may not change it to be static
     * by <code>setModifiers()</code> in <code>CtBehavior</code>.
     *
     * @param methodName the name of the setter
     * @param field      the field accessed.
     */
    public static org.hotswap.agent.javassist.CtMethod setter(String methodName, org.hotswap.agent.javassist.CtField field)
            throws org.hotswap.agent.javassist.CannotCompileException {
        org.hotswap.agent.javassist.bytecode.FieldInfo finfo = field.getFieldInfo2();
        String fieldType = finfo.getDescriptor();
        String desc = "(" + fieldType + ")V";
        org.hotswap.agent.javassist.bytecode.ConstPool cp = finfo.getConstPool();
        org.hotswap.agent.javassist.bytecode.MethodInfo minfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, methodName, desc);
        minfo.setAccessFlags(org.hotswap.agent.javassist.bytecode.AccessFlag.PUBLIC);

        org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp, 3, 3);
        try {
            String fieldName = finfo.getName();
            if ((finfo.getAccessFlags() & org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC) == 0) {
                code.addAload(0);
                code.addLoad(1, field.getType());
                code.addPutfield(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            } else {
                code.addLoad(1, field.getType());
                code.addPutstatic(org.hotswap.agent.javassist.bytecode.Bytecode.THIS, fieldName, fieldType);
            }

            code.addReturn(null);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }

        minfo.setCodeAttribute(code.toCodeAttribute());
        org.hotswap.agent.javassist.CtClass cc = field.getDeclaringClass();
        // a stack map is not needed.
        return new org.hotswap.agent.javassist.CtMethod(minfo, cc);
    }

    /**
     * Creates a method forwarding to a delegate in
     * a super class.  The created method calls a method specified
     * by <code>delegate</code> with all the parameters passed to the
     * created method.  If the delegate method returns a value,
     * the created method returns that value to the caller.
     * The delegate method must be declared in a super class.
     * <p/>
     * <p>The following method is an example of the created method.
     * <p/>
     * <ul><pre>int f(int p, int q) {
     *     return super.f(p, q);
     * }</pre></ul>
     * <p/>
     * <p>The name of the created method can be changed by
     * <code>setName()</code>.
     *
     * @param delegate  the method that the created method forwards to.
     * @param declaring the class to which the created method is
     *                  added.
     */
    public static org.hotswap.agent.javassist.CtMethod delegator(org.hotswap.agent.javassist.CtMethod delegate, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        try {
            return delegator0(delegate, declaring);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    private static org.hotswap.agent.javassist.CtMethod delegator0(org.hotswap.agent.javassist.CtMethod delegate, org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.bytecode.MethodInfo deleInfo = delegate.getMethodInfo2();
        String methodName = deleInfo.getName();
        String desc = deleInfo.getDescriptor();
        org.hotswap.agent.javassist.bytecode.ConstPool cp = declaring.getClassFile2().getConstPool();
        org.hotswap.agent.javassist.bytecode.MethodInfo minfo = new org.hotswap.agent.javassist.bytecode.MethodInfo(cp, methodName, desc);
        minfo.setAccessFlags(deleInfo.getAccessFlags());

        org.hotswap.agent.javassist.bytecode.ExceptionsAttribute eattr = deleInfo.getExceptionsAttribute();
        if (eattr != null)
            minfo.setExceptionsAttribute(
                    (org.hotswap.agent.javassist.bytecode.ExceptionsAttribute) eattr.copy(cp, null));

        org.hotswap.agent.javassist.bytecode.Bytecode code = new org.hotswap.agent.javassist.bytecode.Bytecode(cp, 0, 0);
        boolean isStatic = org.hotswap.agent.javassist.Modifier.isStatic(delegate.getModifiers());
        org.hotswap.agent.javassist.CtClass deleClass = delegate.getDeclaringClass();
        org.hotswap.agent.javassist.CtClass[] params = delegate.getParameterTypes();
        int s;
        if (isStatic) {
            s = code.addLoadParameters(params, 0);
            code.addInvokestatic(deleClass, methodName, desc);
        } else {
            code.addLoad(0, deleClass);
            s = code.addLoadParameters(params, 1);
            code.addInvokespecial(deleClass, methodName, desc);
        }

        code.addReturn(delegate.getReturnType());
        code.setMaxLocals(++s);
        code.setMaxStack(s < 2 ? 2 : s); // for a 2-word return value
        minfo.setCodeAttribute(code.toCodeAttribute());
        // a stack map is not needed. 
        return new org.hotswap.agent.javassist.CtMethod(minfo, declaring);
    }

    /**
     * Creates a wrapped method.  The wrapped method receives parameters
     * in the form of an array of <code>Object</code>.
     * <p/>
     * <p>The body of the created method is a copy of the body of the method
     * specified by <code>body</code>.  However, it is wrapped in
     * parameter-conversion code.
     * <p/>
     * <p>The method specified by <code>body</code> must have this singature:
     * <p/>
     * <ul><code>Object method(Object[] params, &lt;type&gt; cvalue)
     * </code></ul>
     * <p/>
     * <p>The type of the <code>cvalue</code> depends on
     * <code>constParam</code>.
     * If <code>constParam</code> is <code>null</code>, the signature
     * must be:
     * <p/>
     * <ul><code>Object method(Object[] params)</code></ul>
     * <p/>
     * <p>The method body copied from <code>body</code> is wrapped in
     * parameter-conversion code, which converts parameters specified by
     * <code>parameterTypes</code> into an array of <code>Object</code>.
     * The returned value is also converted from the <code>Object</code>
     * type to the type specified by <code>returnType</code>.  Thus,
     * the resulting method body is as follows:
     * <p/>
     * <ul><pre>Object[] params = new Object[] { p0, p1, ... };
     * &lt;<i>type</i>&gt; cvalue = &lt;<i>constant-value</i>&gt;;
     *  <i>... copied method body ...</i>
     * Object result = &lt;<i>returned value</i>&gt;
     * return (<i>&lt;returnType&gt;</i>)result;
     * </pre></ul>
     * <p/>
     * <p>The variables <code>p0</code>, <code>p2</code>, ... represent
     * formal parameters of the created method.
     * The value of <code>cvalue</code> is specified by
     * <code>constParam</code>.
     * <p/>
     * <p>If the type of a parameter or a returned value is a primitive
     * type, then the value is converted into a wrapper object such as
     * <code>java.lang.Integer</code>.  If the type of the returned value
     * is <code>void</code>, the returned value is discarded.
     * <p/>
     * <p><i>Example:</i>
     * <p/>
     * <ul><pre>ClassPool pool = ... ;
     * CtClass vec = pool.makeClass("intVector");
     * vec.setSuperclass(pool.get("java.util.Vector"));
     * CtMethod addMethod = pool.getMethod("Sample", "add0");
     * <p/>
     * CtClass[] argTypes = { CtClass.intType };
     * CtMethod m = CtNewMethod.wrapped(CtClass.voidType, "add", argTypes,
     *                                  null, addMethod, null, vec);
     * vec.addMethod(m);</pre></ul>
     * <p/>
     * <p>where the class <code>Sample</code> is as follows:
     * <p/>
     * <ul><pre>public class Sample extends java.util.Vector {
     *     public Object add0(Object[] args) {
     *         super.addElement(args[0]);
     *         return null;
     *     }
     * }</pre></ul>
     * <p/>
     * <p>This program produces a class <code>intVector</code>:
     * <p/>
     * <ul><pre>public class intVector extends java.util.Vector {
     *     public void add(int p0) {
     *         Object[] args = new Object[] { p0 };
     *         // begin of the copied body
     *         super.addElement(args[0]);
     *         Object result = null;
     *         // end
     *     }
     * }</pre></ul>
     * <p/>
     * <p>Note that the type of the parameter to <code>add()</code> depends
     * only on the value of <code>argTypes</code> passed to
     * <code>CtNewMethod.wrapped()</code>.  Thus, it is easy to
     * modify this program to produce a
     * <code>StringVector</code> class, which is a vector containing
     * only <code>String</code> objects, and other vector classes.
     *
     * @param returnType     the type of the returned value.
     * @param mname          the method name.
     * @param parameterTypes a list of the parameter types.
     * @param exceptionTypes a list of the exception types.
     * @param body           the method body
     *                       (must not be a static method).
     * @param constParam     the constant parameter
     *                       (maybe <code>null</code>).
     * @param declaring      the class to which the created method is
     *                       added.
     */
    public static org.hotswap.agent.javassist.CtMethod wrapped(org.hotswap.agent.javassist.CtClass returnType,
                                                               String mname,
                                                               org.hotswap.agent.javassist.CtClass[] parameterTypes,
                                                               org.hotswap.agent.javassist.CtClass[] exceptionTypes,
                                                               org.hotswap.agent.javassist.CtMethod body, ConstParameter constParam,
                                                               org.hotswap.agent.javassist.CtClass declaring)
            throws org.hotswap.agent.javassist.CannotCompileException {
        return org.hotswap.agent.javassist.CtNewWrappedMethod.wrapped(returnType, mname, parameterTypes,
                exceptionTypes, body, constParam, declaring);
    }
}
