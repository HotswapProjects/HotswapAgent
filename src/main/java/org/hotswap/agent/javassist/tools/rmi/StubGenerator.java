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

package org.hotswap.agent.javassist.tools.rmi;

import java.lang.reflect.Method;
import java.util.Hashtable;

/**
 * A stub-code generator.  It is used for producing a proxy class.
 * <p/>
 * <p>The proxy class for class A is as follows:
 * <p/>
 * <ul><pre>public class A implements Proxy, Serializable {
 *   private ObjectImporter importer;
 *   private int objectId;
 *   public int _getObjectId() { return objectId; }
 *   public A(ObjectImporter oi, int id) {
 *     importer = oi; objectId = id;
 *   }
 * <p/>
 *   ... the same methods that the original class A declares ...
 * }</pre></ul>
 * <p/>
 * <p>Instances of the proxy class is created by an
 * <code>ObjectImporter</code> object.
 */
public class StubGenerator implements org.hotswap.agent.javassist.Translator {
    private static final String fieldImporter = "importer";
    private static final String fieldObjectId = "objectId";
    private static final String accessorObjectId = "_getObjectId";
    private static final String sampleClass = "Sample";

    private org.hotswap.agent.javassist.ClassPool classPool;
    private Hashtable proxyClasses;
    private org.hotswap.agent.javassist.CtMethod forwardMethod;
    private org.hotswap.agent.javassist.CtMethod forwardStaticMethod;

    private org.hotswap.agent.javassist.CtClass[] proxyConstructorParamTypes;
    private org.hotswap.agent.javassist.CtClass[] interfacesForProxy;
    private org.hotswap.agent.javassist.CtClass[] exceptionForProxy;

    /**
     * Constructs a stub-code generator.
     */
    public StubGenerator() {
        proxyClasses = new Hashtable();
    }

    /**
     * Initializes the object.
     * This is a method declared in Translator.
     *
     * @see org.hotswap.agent.javassist.Translator#start(org.hotswap.agent.javassist.ClassPool)
     */
    public void start(org.hotswap.agent.javassist.ClassPool pool) throws org.hotswap.agent.javassist.NotFoundException {
        classPool = pool;
        org.hotswap.agent.javassist.CtClass c = pool.get(sampleClass);
        forwardMethod = c.getDeclaredMethod("forward");
        forwardStaticMethod = c.getDeclaredMethod("forwardStatic");

        proxyConstructorParamTypes
                = pool.get(new String[]{"ObjectImporter",
                "int"});
        interfacesForProxy
                = pool.get(new String[]{"java.io.Serializable",
                "Proxy"});
        exceptionForProxy
                = new org.hotswap.agent.javassist.CtClass[]{pool.get("RemoteException")};
    }

    /**
     * Does nothing.
     * This is a method declared in Translator.
     *
     * @see org.hotswap.agent.javassist.Translator#onLoad(org.hotswap.agent.javassist.ClassPool, String)
     */
    public void onLoad(org.hotswap.agent.javassist.ClassPool pool, String classname) {
    }

    /**
     * Returns <code>true</code> if the specified class is a proxy class
     * recorded by <code>makeProxyClass()</code>.
     *
     * @param name a fully-qualified class name
     */
    public boolean isProxyClass(String name) {
        return proxyClasses.get(name) != null;
    }

    /**
     * Makes a proxy class.  The produced class is substituted
     * for the original class.
     *
     * @param clazz the class referenced
     *              through the proxy class.
     * @return <code>false</code> if the proxy class
     * has been already produced.
     */
    public synchronized boolean makeProxyClass(Class clazz)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        String classname = clazz.getName();
        if (proxyClasses.get(classname) != null)
            return false;
        else {
            org.hotswap.agent.javassist.CtClass ctclazz = produceProxyClass(classPool.get(classname),
                    clazz);
            proxyClasses.put(classname, ctclazz);
            modifySuperclass(ctclazz);
            return true;
        }
    }

    private org.hotswap.agent.javassist.CtClass produceProxyClass(org.hotswap.agent.javassist.CtClass orgclass, Class orgRtClass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        int modify = orgclass.getModifiers();
        if (org.hotswap.agent.javassist.Modifier.isAbstract(modify) || org.hotswap.agent.javassist.Modifier.isNative(modify)
                || !org.hotswap.agent.javassist.Modifier.isPublic(modify))
            throw new org.hotswap.agent.javassist.CannotCompileException(orgclass.getName()
                    + " must be public, non-native, and non-abstract.");

        org.hotswap.agent.javassist.CtClass proxy = classPool.makeClass(orgclass.getName(),
                orgclass.getSuperclass());

        proxy.setInterfaces(interfacesForProxy);

        org.hotswap.agent.javassist.CtField f
                = new org.hotswap.agent.javassist.CtField(classPool.get("ObjectImporter"),
                fieldImporter, proxy);
        f.setModifiers(org.hotswap.agent.javassist.Modifier.PRIVATE);
        proxy.addField(f, org.hotswap.agent.javassist.CtField.Initializer.byParameter(0));

        f = new org.hotswap.agent.javassist.CtField(org.hotswap.agent.javassist.CtClass.intType, fieldObjectId, proxy);
        f.setModifiers(org.hotswap.agent.javassist.Modifier.PRIVATE);
        proxy.addField(f, org.hotswap.agent.javassist.CtField.Initializer.byParameter(1));

        proxy.addMethod(org.hotswap.agent.javassist.CtNewMethod.getter(accessorObjectId, f));

        proxy.addConstructor(org.hotswap.agent.javassist.CtNewConstructor.defaultConstructor(proxy));
        org.hotswap.agent.javassist.CtConstructor cons
                = org.hotswap.agent.javassist.CtNewConstructor.skeleton(proxyConstructorParamTypes,
                null, proxy);
        proxy.addConstructor(cons);

        try {
            addMethods(proxy, orgRtClass.getMethods());
            return proxy;
        } catch (SecurityException e) {
            throw new org.hotswap.agent.javassist.CannotCompileException(e);
        }
    }

    private org.hotswap.agent.javassist.CtClass toCtClass(Class rtclass) throws org.hotswap.agent.javassist.NotFoundException {
        String name;
        if (!rtclass.isArray())
            name = rtclass.getName();
        else {
            StringBuffer sbuf = new StringBuffer();
            do {
                sbuf.append("[]");
                rtclass = rtclass.getComponentType();
            } while (rtclass.isArray());
            sbuf.insert(0, rtclass.getName());
            name = sbuf.toString();
        }

        return classPool.get(name);
    }

    private org.hotswap.agent.javassist.CtClass[] toCtClass(Class[] rtclasses) throws org.hotswap.agent.javassist.NotFoundException {
        int n = rtclasses.length;
        org.hotswap.agent.javassist.CtClass[] ctclasses = new org.hotswap.agent.javassist.CtClass[n];
        for (int i = 0; i < n; ++i)
            ctclasses[i] = toCtClass(rtclasses[i]);

        return ctclasses;
    }

    /* ms must not be an array of CtMethod.  To invoke a method ms[i]
     * on a server, a client must send i to the server.
     */
    private void addMethods(org.hotswap.agent.javassist.CtClass proxy, Method[] ms)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtMethod wmethod;
        for (int i = 0; i < ms.length; ++i) {
            Method m = ms[i];
            int mod = m.getModifiers();
            if (m.getDeclaringClass() != Object.class
                    && !org.hotswap.agent.javassist.Modifier.isFinal(mod))
                if (org.hotswap.agent.javassist.Modifier.isPublic(mod)) {
                    org.hotswap.agent.javassist.CtMethod body;
                    if (org.hotswap.agent.javassist.Modifier.isStatic(mod))
                        body = forwardStaticMethod;
                    else
                        body = forwardMethod;

                    wmethod
                            = org.hotswap.agent.javassist.CtNewMethod.wrapped(toCtClass(m.getReturnType()),
                            m.getName(),
                            toCtClass(m.getParameterTypes()),
                            exceptionForProxy,
                            body,
                            org.hotswap.agent.javassist.CtMethod.ConstParameter.integer(i),
                            proxy);
                    wmethod.setModifiers(mod);
                    proxy.addMethod(wmethod);
                } else if (!org.hotswap.agent.javassist.Modifier.isProtected(mod)
                        && !org.hotswap.agent.javassist.Modifier.isPrivate(mod))
                    // if package method
                    throw new org.hotswap.agent.javassist.CannotCompileException(
                            "the methods must be public, protected, or private.");
        }
    }

    /**
     * Adds a default constructor to the super classes.
     */
    private void modifySuperclass(org.hotswap.agent.javassist.CtClass orgclass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtClass superclazz;
        for (; ; orgclass = superclazz) {
            superclazz = orgclass.getSuperclass();
            if (superclazz == null)
                break;

            try {
                superclazz.getDeclaredConstructor(null);
                break;  // the constructor with no arguments is found.
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
            }

            superclazz.addConstructor(
                    org.hotswap.agent.javassist.CtNewConstructor.defaultConstructor(superclazz));
        }
    }
}
