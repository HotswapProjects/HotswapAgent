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

package org.hotswap.agent.javassist.tools.reflect;

import org.hotswap.agent.javassist.CtMethod.ConstParameter;
import org.hotswap.agent.javassist.bytecode.BadBytecode;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.javassist.bytecode.MethodInfo;

import java.util.Iterator;

/**
 * The class implementing the behavioral reflection mechanism.
 * <p/>
 * <p>If a class is reflective,
 * then all the method invocations on every
 * instance of that class are intercepted by the runtime
 * metaobject controlling that instance.  The methods inherited from the
 * super classes are also intercepted except final methods.  To intercept
 * a final method in a super class, that super class must be also reflective.
 * <p/>
 * <p>To do this, the original class file representing a reflective class:
 * <p/>
 * <ul><pre>
 * class Person {
 *   public int f(int i) { return i + 1; }
 *   public int value;
 * }
 * </pre></ul>
 * <p/>
 * <p>is modified so that it represents a class:
 * <p/>
 * <ul><pre>
 * class Person implements Metalevel {
 *   public int _original_f(int i) { return i + 1; }
 *   public int f(int i) { <i>delegate to the metaobject</i> }
 * <p/>
 *   public int value;
 *   public int _r_value() { <i>read "value"</i> }
 *   public void _w_value(int v) { <i>write "value"</i> }
 * <p/>
 *   public ClassMetaobject _getClass() { <i>return a class metaobject</i> }
 *   public Metaobject _getMetaobject() { <i>return a metaobject</i> }
 *   public void _setMetaobject(Metaobject m) { <i>change a metaobject</i> }
 * }
 * </pre></ul>
 *
 * @see org.hotswap.agent.javassist.tools.reflect.ClassMetaobject
 * @see Metaobject
 * @see Loader
 * @see Compiler
 */
public class Reflection implements org.hotswap.agent.javassist.Translator {

    static final String classobjectField = "_classobject";
    static final String classobjectAccessor = "_getClass";
    static final String metaobjectField = "_metaobject";
    static final String metaobjectGetter = "_getMetaobject";
    static final String metaobjectSetter = "_setMetaobject";
    static final String readPrefix = "_r_";
    static final String writePrefix = "_w_";

    static final String metaobjectClassName = "Metaobject";
    static final String classMetaobjectClassName
            = "ClassMetaobject";

    protected org.hotswap.agent.javassist.CtMethod trapMethod, trapStaticMethod;
    protected org.hotswap.agent.javassist.CtMethod trapRead, trapWrite;
    protected org.hotswap.agent.javassist.CtClass[] readParam;

    protected org.hotswap.agent.javassist.ClassPool classPool;
    protected org.hotswap.agent.javassist.CodeConverter converter;

    private boolean isExcluded(String name) {
        return name.startsWith(org.hotswap.agent.javassist.tools.reflect.ClassMetaobject.methodPrefix)
                || name.equals(classobjectAccessor)
                || name.equals(metaobjectSetter)
                || name.equals(metaobjectGetter)
                || name.startsWith(readPrefix)
                || name.startsWith(writePrefix);
    }

    /**
     * Constructs a new <code>Reflection</code> object.
     */
    public Reflection() {
        classPool = null;
        converter = new org.hotswap.agent.javassist.CodeConverter();
    }

    /**
     * Initializes the object.
     */
    public void start(org.hotswap.agent.javassist.ClassPool pool) throws org.hotswap.agent.javassist.NotFoundException {
        classPool = pool;
        final String msg
                = "Sample is not found or broken.";
        try {
            org.hotswap.agent.javassist.CtClass c = classPool.get("Sample");
            rebuildClassFile(c.getClassFile());
            trapMethod = c.getDeclaredMethod("trap");
            trapStaticMethod = c.getDeclaredMethod("trapStatic");
            trapRead = c.getDeclaredMethod("trapRead");
            trapWrite = c.getDeclaredMethod("trapWrite");
            readParam
                    = new org.hotswap.agent.javassist.CtClass[]{classPool.get("java.lang.Object")};
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            throw new RuntimeException(msg);
        } catch (BadBytecode e) {
            throw new RuntimeException(msg);
        }
    }

    /**
     * Inserts hooks for intercepting accesses to the fields declared
     * in reflective classes.
     */
    public void onLoad(org.hotswap.agent.javassist.ClassPool pool, String classname)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtClass clazz = pool.get(classname);
        clazz.instrument(converter);
    }

    /**
     * Produces a reflective class.
     * If the super class is also made reflective, it must be done
     * before the sub class.
     *
     * @param classname  the name of the reflective class
     * @param metaobject the class name of metaobjects.
     * @param metaclass  the class name of the class metaobject.
     * @return <code>false</code>       if the class is already reflective.
     * @see Metaobject
     * @see org.hotswap.agent.javassist.tools.reflect.ClassMetaobject
     */
    public boolean makeReflective(String classname,
                                  String metaobject, String metaclass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        return makeReflective(classPool.get(classname),
                classPool.get(metaobject),
                classPool.get(metaclass));
    }

    /**
     * Produces a reflective class.
     * If the super class is also made reflective, it must be done
     * before the sub class.
     *
     * @param clazz      the reflective class.
     * @param metaobject the class of metaobjects.
     *                   It must be a subclass of
     *                   <code>Metaobject</code>.
     * @param metaclass  the class of the class metaobject.
     *                   It must be a subclass of
     *                   <code>ClassMetaobject</code>.
     * @return <code>false</code>       if the class is already reflective.
     * @see Metaobject
     * @see org.hotswap.agent.javassist.tools.reflect.ClassMetaobject
     */
    public boolean makeReflective(Class clazz,
                                  Class metaobject, Class metaclass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        return makeReflective(clazz.getName(), metaobject.getName(),
                metaclass.getName());
    }

    /**
     * Produces a reflective class.  It modifies the given
     * <code>CtClass</code> object and makes it reflective.
     * If the super class is also made reflective, it must be done
     * before the sub class.
     *
     * @param clazz      the reflective class.
     * @param metaobject the class of metaobjects.
     *                   It must be a subclass of
     *                   <code>Metaobject</code>.
     * @param metaclass  the class of the class metaobject.
     *                   It must be a subclass of
     *                   <code>ClassMetaobject</code>.
     * @return <code>false</code>       if the class is already reflective.
     * @see Metaobject
     * @see org.hotswap.agent.javassist.tools.reflect.ClassMetaobject
     */
    public boolean makeReflective(org.hotswap.agent.javassist.CtClass clazz,
                                  org.hotswap.agent.javassist.CtClass metaobject, org.hotswap.agent.javassist.CtClass metaclass)
            throws org.hotswap.agent.javassist.CannotCompileException, CannotReflectException,
            org.hotswap.agent.javassist.NotFoundException {
        if (clazz.isInterface())
            throw new CannotReflectException(
                    "Cannot reflect an interface: " + clazz.getName());

        if (clazz.subclassOf(classPool.get(classMetaobjectClassName)))
            throw new CannotReflectException(
                    "Cannot reflect a subclass of ClassMetaobject: "
                            + clazz.getName());

        if (clazz.subclassOf(classPool.get(metaobjectClassName)))
            throw new CannotReflectException(
                    "Cannot reflect a subclass of Metaobject: "
                            + clazz.getName());

        registerReflectiveClass(clazz);
        return modifyClassfile(clazz, metaobject, metaclass);
    }

    /**
     * Registers a reflective class.  The field accesses to the instances
     * of this class are instrumented.
     */
    private void registerReflectiveClass(org.hotswap.agent.javassist.CtClass clazz) {
        org.hotswap.agent.javassist.CtField[] fs = clazz.getDeclaredFields();
        for (int i = 0; i < fs.length; ++i) {
            org.hotswap.agent.javassist.CtField f = fs[i];
            int mod = f.getModifiers();
            if ((mod & org.hotswap.agent.javassist.Modifier.PUBLIC) != 0 && (mod & org.hotswap.agent.javassist.Modifier.FINAL) == 0) {
                String name = f.getName();
                converter.replaceFieldRead(f, clazz, readPrefix + name);
                converter.replaceFieldWrite(f, clazz, writePrefix + name);
            }
        }
    }

    private boolean modifyClassfile(org.hotswap.agent.javassist.CtClass clazz, org.hotswap.agent.javassist.CtClass metaobject,
                                    org.hotswap.agent.javassist.CtClass metaclass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        if (clazz.getAttribute("Reflective") != null)
            return false;       // this is already reflective.
        else
            clazz.setAttribute("Reflective", new byte[0]);

        org.hotswap.agent.javassist.CtClass mlevel = classPool.get("Metalevel");
        boolean addMeta = !clazz.subtypeOf(mlevel);
        if (addMeta)
            clazz.addInterface(mlevel);

        processMethods(clazz, addMeta);
        processFields(clazz);

        org.hotswap.agent.javassist.CtField f;
        if (addMeta) {
            f = new org.hotswap.agent.javassist.CtField(classPool.get("Metaobject"),
                    metaobjectField, clazz);
            f.setModifiers(org.hotswap.agent.javassist.Modifier.PROTECTED);
            clazz.addField(f, org.hotswap.agent.javassist.CtField.Initializer.byNewWithParams(metaobject));

            clazz.addMethod(org.hotswap.agent.javassist.CtNewMethod.getter(metaobjectGetter, f));
            clazz.addMethod(org.hotswap.agent.javassist.CtNewMethod.setter(metaobjectSetter, f));
        }

        f = new org.hotswap.agent.javassist.CtField(classPool.get("ClassMetaobject"),
                classobjectField, clazz);
        f.setModifiers(org.hotswap.agent.javassist.Modifier.PRIVATE | org.hotswap.agent.javassist.Modifier.STATIC);
        clazz.addField(f, org.hotswap.agent.javassist.CtField.Initializer.byNew(metaclass,
                new String[]{clazz.getName()}));

        clazz.addMethod(org.hotswap.agent.javassist.CtNewMethod.getter(classobjectAccessor, f));
        return true;
    }

    private void processMethods(org.hotswap.agent.javassist.CtClass clazz, boolean dontSearch)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtMethod[] ms = clazz.getMethods();
        for (int i = 0; i < ms.length; ++i) {
            org.hotswap.agent.javassist.CtMethod m = ms[i];
            int mod = m.getModifiers();
            if (org.hotswap.agent.javassist.Modifier.isPublic(mod) && !org.hotswap.agent.javassist.Modifier.isAbstract(mod))
                processMethods0(mod, clazz, m, i, dontSearch);
        }
    }

    private void processMethods0(int mod, org.hotswap.agent.javassist.CtClass clazz,
                                 org.hotswap.agent.javassist.CtMethod m, int identifier, boolean dontSearch)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtMethod body;
        String name = m.getName();

        if (isExcluded(name))   // internally-used method inherited
            return;             // from a reflective class.

        org.hotswap.agent.javassist.CtMethod m2;
        if (m.getDeclaringClass() == clazz) {
            if (org.hotswap.agent.javassist.Modifier.isNative(mod))
                return;

            m2 = m;
            if (org.hotswap.agent.javassist.Modifier.isFinal(mod)) {
                mod &= ~org.hotswap.agent.javassist.Modifier.FINAL;
                m2.setModifiers(mod);
            }
        } else {
            if (org.hotswap.agent.javassist.Modifier.isFinal(mod))
                return;

            mod &= ~org.hotswap.agent.javassist.Modifier.NATIVE;
            m2 = org.hotswap.agent.javassist.CtNewMethod.delegator(findOriginal(m, dontSearch), clazz);
            m2.setModifiers(mod);
            clazz.addMethod(m2);
        }

        m2.setName(org.hotswap.agent.javassist.tools.reflect.ClassMetaobject.methodPrefix + identifier
                + "_" + name);

        if (org.hotswap.agent.javassist.Modifier.isStatic(mod))
            body = trapStaticMethod;
        else
            body = trapMethod;

        org.hotswap.agent.javassist.CtMethod wmethod
                = org.hotswap.agent.javassist.CtNewMethod.wrapped(m.getReturnType(), name,
                m.getParameterTypes(), m.getExceptionTypes(),
                body, ConstParameter.integer(identifier),
                clazz);
        wmethod.setModifiers(mod);
        clazz.addMethod(wmethod);
    }

    private org.hotswap.agent.javassist.CtMethod findOriginal(org.hotswap.agent.javassist.CtMethod m, boolean dontSearch)
            throws org.hotswap.agent.javassist.NotFoundException {
        if (dontSearch)
            return m;

        String name = m.getName();
        org.hotswap.agent.javassist.CtMethod[] ms = m.getDeclaringClass().getDeclaredMethods();
        for (int i = 0; i < ms.length; ++i) {
            String orgName = ms[i].getName();
            if (orgName.endsWith(name)
                    && orgName.startsWith(org.hotswap.agent.javassist.tools.reflect.ClassMetaobject.methodPrefix)
                    && ms[i].getSignature().equals(m.getSignature()))
                return ms[i];
        }

        return m;
    }

    private void processFields(org.hotswap.agent.javassist.CtClass clazz)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtField[] fs = clazz.getDeclaredFields();
        for (int i = 0; i < fs.length; ++i) {
            org.hotswap.agent.javassist.CtField f = fs[i];
            int mod = f.getModifiers();
            if ((mod & org.hotswap.agent.javassist.Modifier.PUBLIC) != 0 && (mod & org.hotswap.agent.javassist.Modifier.FINAL) == 0) {
                mod |= org.hotswap.agent.javassist.Modifier.STATIC;
                String name = f.getName();
                org.hotswap.agent.javassist.CtClass ftype = f.getType();
                org.hotswap.agent.javassist.CtMethod wmethod
                        = org.hotswap.agent.javassist.CtNewMethod.wrapped(ftype, readPrefix + name,
                        readParam, null, trapRead,
                        ConstParameter.string(name),
                        clazz);
                wmethod.setModifiers(mod);
                clazz.addMethod(wmethod);
                org.hotswap.agent.javassist.CtClass[] writeParam = new org.hotswap.agent.javassist.CtClass[2];
                writeParam[0] = classPool.get("java.lang.Object");
                writeParam[1] = ftype;
                wmethod = org.hotswap.agent.javassist.CtNewMethod.wrapped(org.hotswap.agent.javassist.CtClass.voidType,
                        writePrefix + name,
                        writeParam, null, trapWrite,
                        ConstParameter.string(name), clazz);
                wmethod.setModifiers(mod);
                clazz.addMethod(wmethod);
            }
        }
    }

    public void rebuildClassFile(ClassFile cf) throws BadBytecode {
        if (ClassFile.MAJOR_VERSION < ClassFile.JAVA_6)
            return;

        Iterator methods = cf.getMethods().iterator();
        while (methods.hasNext()) {
            MethodInfo mi = (MethodInfo) methods.next();
            mi.rebuildStackMap(classPool);
        }
    }
}
