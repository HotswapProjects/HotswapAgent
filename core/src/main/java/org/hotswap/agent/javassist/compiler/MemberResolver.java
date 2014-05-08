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

package org.hotswap.agent.javassist.compiler;

import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

/* Code generator methods depending on javassist.* classes.
 */
public class MemberResolver implements TokenId {
    private org.hotswap.agent.javassist.ClassPool classPool;

    public MemberResolver(org.hotswap.agent.javassist.ClassPool cp) {
        classPool = cp;
    }

    public org.hotswap.agent.javassist.ClassPool getClassPool() {
        return classPool;
    }

    private static void fatal() throws CompileError {
        throw new CompileError("fatal");
    }

    public static class Method {
        public org.hotswap.agent.javassist.CtClass declaring;
        public org.hotswap.agent.javassist.bytecode.MethodInfo info;
        public int notmatch;

        public Method(org.hotswap.agent.javassist.CtClass c, org.hotswap.agent.javassist.bytecode.MethodInfo i, int n) {
            declaring = c;
            info = i;
            notmatch = n;
        }

        /**
         * Returns true if the invoked method is static.
         */
        public boolean isStatic() {
            int acc = info.getAccessFlags();
            return (acc & org.hotswap.agent.javassist.bytecode.AccessFlag.STATIC) != 0;
        }
    }

    public Method lookupMethod(org.hotswap.agent.javassist.CtClass clazz, org.hotswap.agent.javassist.CtClass currentClass, org.hotswap.agent.javassist.bytecode.MethodInfo current,
                               String methodName,
                               int[] argTypes, int[] argDims,
                               String[] argClassNames)
            throws CompileError {
        Method maybe = null;
        // to enable the creation of a recursively called method
        if (current != null && clazz == currentClass)
            if (current.getName().equals(methodName)) {
                int res = compareSignature(current.getDescriptor(),
                        argTypes, argDims, argClassNames);
                if (res != NO) {
                    Method r = new Method(clazz, current, res);
                    if (res == YES)
                        return r;
                    else
                        maybe = r;
                }
            }

        Method m = lookupMethod(clazz, methodName, argTypes, argDims,
                argClassNames, maybe != null);
        if (m != null)
            return m;
        else
            return maybe;
    }

    private Method lookupMethod(org.hotswap.agent.javassist.CtClass clazz, String methodName,
                                int[] argTypes, int[] argDims,
                                String[] argClassNames, boolean onlyExact)
            throws CompileError {
        Method maybe = null;
        org.hotswap.agent.javassist.bytecode.ClassFile cf = clazz.getClassFile2();
        // If the class is an array type, the class file is null.
        // If so, search the super class java.lang.Object for clone() etc.
        if (cf != null) {
            List list = cf.getMethods();
            int n = list.size();
            for (int i = 0; i < n; ++i) {
                org.hotswap.agent.javassist.bytecode.MethodInfo minfo = (org.hotswap.agent.javassist.bytecode.MethodInfo) list.get(i);
                if (minfo.getName().equals(methodName)) {
                    int res = compareSignature(minfo.getDescriptor(),
                            argTypes, argDims, argClassNames);
                    if (res != NO) {
                        Method r = new Method(clazz, minfo, res);
                        if (res == YES)
                            return r;
                        else if (maybe == null || maybe.notmatch > res)
                            maybe = r;
                    }
                }
            }
        }

        if (onlyExact)
            maybe = null;
        else
            onlyExact = maybe != null;

        int mod = clazz.getModifiers();
        boolean isIntf = org.hotswap.agent.javassist.Modifier.isInterface(mod);
        try {
            // skip searching java.lang.Object if clazz is an interface type.
            if (!isIntf) {
                org.hotswap.agent.javassist.CtClass pclazz = clazz.getSuperclass();
                if (pclazz != null) {
                    Method r = lookupMethod(pclazz, methodName, argTypes,
                            argDims, argClassNames, onlyExact);
                    if (r != null)
                        return r;
                }
            }
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
        }

        if (isIntf || org.hotswap.agent.javassist.Modifier.isAbstract(mod))
            try {
                org.hotswap.agent.javassist.CtClass[] ifs = clazz.getInterfaces();
                int size = ifs.length;
                for (int i = 0; i < size; ++i) {
                    Method r = lookupMethod(ifs[i], methodName,
                            argTypes, argDims, argClassNames,
                            onlyExact);
                    if (r != null)
                        return r;
                }

                if (isIntf) {
                    // finally search java.lang.Object.
                    org.hotswap.agent.javassist.CtClass pclazz = clazz.getSuperclass();
                    if (pclazz != null) {
                        Method r = lookupMethod(pclazz, methodName, argTypes,
                                argDims, argClassNames, onlyExact);
                        if (r != null)
                            return r;
                    }
                }
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
            }

        return maybe;
    }

    private static final int YES = 0;
    private static final int NO = -1;

    /*
     * Returns YES if actual parameter types matches the given signature.
     *
     * argTypes, argDims, and argClassNames represent actual parameters.
     *
     * This method does not correctly implement the Java method dispatch
     * algorithm.
     *
     * If some of the parameter types exactly match but others are subtypes of
     * the corresponding type in the signature, this method returns the number
     * of parameter types that do not exactly match.
     */
    private int compareSignature(String desc, int[] argTypes,
                                 int[] argDims, String[] argClassNames)
            throws CompileError {
        int result = YES;
        int i = 1;
        int nArgs = argTypes.length;
        if (nArgs != org.hotswap.agent.javassist.bytecode.Descriptor.numOfParameters(desc))
            return NO;

        int len = desc.length();
        for (int n = 0; i < len; ++n) {
            char c = desc.charAt(i++);
            if (c == ')')
                return (n == nArgs ? result : NO);
            else if (n >= nArgs)
                return NO;

            int dim = 0;
            while (c == '[') {
                ++dim;
                c = desc.charAt(i++);
            }

            if (argTypes[n] == NULL) {
                if (dim == 0 && c != 'L')
                    return NO;

                if (c == 'L')
                    i = desc.indexOf(';', i) + 1;
            } else if (argDims[n] != dim) {
                if (!(dim == 0 && c == 'L'
                        && desc.startsWith("java/lang/Object;", i)))
                    return NO;

                // if the thread reaches here, c must be 'L'.
                i = desc.indexOf(';', i) + 1;
                result++;
                if (i <= 0)
                    return NO;  // invalid descriptor?
            } else if (c == 'L') {        // not compare
                int j = desc.indexOf(';', i);
                if (j < 0 || argTypes[n] != CLASS)
                    return NO;

                String cname = desc.substring(i, j);
                if (!cname.equals(argClassNames[n])) {
                    org.hotswap.agent.javassist.CtClass clazz = lookupClassByJvmName(argClassNames[n]);
                    try {
                        if (clazz.subtypeOf(lookupClassByJvmName(cname)))
                            result++;
                        else
                            return NO;
                    } catch (org.hotswap.agent.javassist.NotFoundException e) {
                        result++; // should be NO?
                    }
                }

                i = j + 1;
            } else {
                int t = descToType(c);
                int at = argTypes[n];
                if (t != at)
                    if (t == INT
                            && (at == SHORT || at == BYTE || at == CHAR))
                        result++;
                    else
                        return NO;
            }
        }

        return NO;
    }

    /**
     * Only used by fieldAccess() in MemberCodeGen and TypeChecker.
     *
     * @param jvmClassName a JVM class name.  e.g. java/lang/String
     * @see #lookupClass(String, boolean)
     */
    public org.hotswap.agent.javassist.CtField lookupFieldByJvmName2(String jvmClassName, org.hotswap.agent.javassist.compiler.ast.Symbol fieldSym,
                                                                     org.hotswap.agent.javassist.compiler.ast.ASTree expr) throws org.hotswap.agent.javassist.compiler.NoFieldException {
        String field = fieldSym.get();
        org.hotswap.agent.javassist.CtClass cc = null;
        try {
            cc = lookupClass(jvmToJavaName(jvmClassName), true);
        } catch (CompileError e) {
            // EXPR might be part of a qualified class name.
            throw new org.hotswap.agent.javassist.compiler.NoFieldException(jvmClassName + "/" + field, expr);
        }

        try {
            return cc.getField(field);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            // maybe an inner class.
            jvmClassName = javaToJvmName(cc.getName());
            throw new org.hotswap.agent.javassist.compiler.NoFieldException(jvmClassName + "$" + field, expr);
        }
    }

    /**
     * @param jvmClassName a JVM class name.  e.g. java/lang/String
     */
    public org.hotswap.agent.javassist.CtField lookupFieldByJvmName(String jvmClassName, org.hotswap.agent.javassist.compiler.ast.Symbol fieldName)
            throws CompileError {
        return lookupField(jvmToJavaName(jvmClassName), fieldName);
    }

    /**
     * @param name a qualified class name. e.g. java.lang.String
     */
    public org.hotswap.agent.javassist.CtField lookupField(String className, org.hotswap.agent.javassist.compiler.ast.Symbol fieldName)
            throws CompileError {
        org.hotswap.agent.javassist.CtClass cc = lookupClass(className, false);
        try {
            return cc.getField(fieldName.get());
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
        }
        throw new CompileError("no such field: " + fieldName.get());
    }

    public org.hotswap.agent.javassist.CtClass lookupClassByName(org.hotswap.agent.javassist.compiler.ast.ASTList name) throws CompileError {
        return lookupClass(org.hotswap.agent.javassist.compiler.ast.Declarator.astToClassName(name, '.'), false);
    }

    public org.hotswap.agent.javassist.CtClass lookupClassByJvmName(String jvmName) throws CompileError {
        return lookupClass(jvmToJavaName(jvmName), false);
    }

    public org.hotswap.agent.javassist.CtClass lookupClass(org.hotswap.agent.javassist.compiler.ast.Declarator decl) throws CompileError {
        return lookupClass(decl.getType(), decl.getArrayDim(),
                decl.getClassName());
    }

    /**
     * @parma classname         jvm class name.
     */
    public org.hotswap.agent.javassist.CtClass lookupClass(int type, int dim, String classname)
            throws CompileError {
        String cname = "";
        org.hotswap.agent.javassist.CtClass clazz;
        if (type == CLASS) {
            clazz = lookupClassByJvmName(classname);
            if (dim > 0)
                cname = clazz.getName();
            else
                return clazz;
        } else
            cname = getTypeName(type);

        while (dim-- > 0)
            cname += "[]";

        return lookupClass(cname, false);
    }

    /*
     * type cannot be CLASS
     */
    static String getTypeName(int type) throws CompileError {
        String cname = "";
        switch (type) {
            case BOOLEAN:
                cname = "boolean";
                break;
            case CHAR:
                cname = "char";
                break;
            case BYTE:
                cname = "byte";
                break;
            case SHORT:
                cname = "short";
                break;
            case INT:
                cname = "int";
                break;
            case LONG:
                cname = "long";
                break;
            case FLOAT:
                cname = "float";
                break;
            case DOUBLE:
                cname = "double";
                break;
            case VOID:
                cname = "void";
                break;
            default:
                fatal();
        }

        return cname;
    }

    /**
     * @param name a qualified class name. e.g. java.lang.String
     */
    public org.hotswap.agent.javassist.CtClass lookupClass(String name, boolean notCheckInner)
            throws CompileError {
        Hashtable cache = getInvalidNames();
        Object found = cache.get(name);
        if (found == INVALID)
            throw new CompileError("no such class: " + name);
        else if (found != null)
            try {
                return classPool.get((String) found);
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
            }

        org.hotswap.agent.javassist.CtClass cc = null;
        try {
            cc = lookupClass0(name, notCheckInner);
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            cc = searchImports(name);
        }

        cache.put(name, cc.getName());
        return cc;
    }

    private static final String INVALID = "<invalid>";
    private static WeakHashMap invalidNamesMap = new WeakHashMap();
    private Hashtable invalidNames = null;

    // for unit tests
    public static int getInvalidMapSize() {
        return invalidNamesMap.size();
    }

    private Hashtable getInvalidNames() {
        Hashtable ht = invalidNames;
        if (ht == null) {
            synchronized (MemberResolver.class) {
                WeakReference ref = (WeakReference) invalidNamesMap.get(classPool);
                if (ref != null)
                    ht = (Hashtable) ref.get();

                if (ht == null) {
                    ht = new Hashtable();
                    invalidNamesMap.put(classPool, new WeakReference(ht));
                }
            }

            invalidNames = ht;
        }

        return ht;
    }

    private org.hotswap.agent.javassist.CtClass searchImports(String orgName)
            throws CompileError {
        if (orgName.indexOf('.') < 0) {
            Iterator it = classPool.getImportedPackages();
            while (it.hasNext()) {
                String pac = (String) it.next();
                String fqName = pac + '.' + orgName;
                try {
                    return classPool.get(fqName);
                } catch (org.hotswap.agent.javassist.NotFoundException e) {
                    try {
                        if (pac.endsWith("." + orgName))
                            return classPool.get(pac);
                    } catch (org.hotswap.agent.javassist.NotFoundException e2) {
                    }
                }
            }
        }

        getInvalidNames().put(orgName, INVALID);
        throw new CompileError("no such class: " + orgName);
    }

    private org.hotswap.agent.javassist.CtClass lookupClass0(String classname, boolean notCheckInner)
            throws org.hotswap.agent.javassist.NotFoundException {
        org.hotswap.agent.javassist.CtClass cc = null;
        do {
            try {
                cc = classPool.get(classname);
            } catch (org.hotswap.agent.javassist.NotFoundException e) {
                int i = classname.lastIndexOf('.');
                if (notCheckInner || i < 0)
                    throw e;
                else {
                    StringBuffer sbuf = new StringBuffer(classname);
                    sbuf.setCharAt(i, '$');
                    classname = sbuf.toString();
                }
            }
        } while (cc == null);
        return cc;
    }

    /* Converts a class name into a JVM-internal representation.
     *
     * It may also expand a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    public String resolveClassName(org.hotswap.agent.javassist.compiler.ast.ASTList name) throws CompileError {
        if (name == null)
            return null;
        else
            return javaToJvmName(lookupClassByName(name).getName());
    }

    /* Expands a simple class name to java.lang.*.
     * For example, this converts Object into java/lang/Object.
     */
    public String resolveJvmClassName(String jvmName) throws CompileError {
        if (jvmName == null)
            return null;
        else
            return javaToJvmName(lookupClassByJvmName(jvmName).getName());
    }

    public static org.hotswap.agent.javassist.CtClass getSuperclass(org.hotswap.agent.javassist.CtClass c) throws CompileError {
        try {
            org.hotswap.agent.javassist.CtClass sc = c.getSuperclass();
            if (sc != null)
                return sc;
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
        }
        throw new CompileError("cannot find the super class of "
                + c.getName());
    }

    public static String javaToJvmName(String classname) {
        return classname.replace('.', '/');
    }

    public static String jvmToJavaName(String classname) {
        return classname.replace('/', '.');
    }

    public static int descToType(char c) throws CompileError {
        switch (c) {
            case 'Z':
                return BOOLEAN;
            case 'C':
                return CHAR;
            case 'B':
                return BYTE;
            case 'S':
                return SHORT;
            case 'I':
                return INT;
            case 'J':
                return LONG;
            case 'F':
                return FLOAT;
            case 'D':
                return DOUBLE;
            case 'V':
                return VOID;
            case 'L':
            case '[':
                return CLASS;
            default:
                fatal();
                return VOID;    // never reach here
        }
    }

    public static int getModifiers(org.hotswap.agent.javassist.compiler.ast.ASTList mods) {
        int m = 0;
        while (mods != null) {
            org.hotswap.agent.javassist.compiler.ast.Keyword k = (org.hotswap.agent.javassist.compiler.ast.Keyword) mods.head();
            mods = mods.tail();
            switch (k.get()) {
                case STATIC:
                    m |= org.hotswap.agent.javassist.Modifier.STATIC;
                    break;
                case FINAL:
                    m |= org.hotswap.agent.javassist.Modifier.FINAL;
                    break;
                case SYNCHRONIZED:
                    m |= org.hotswap.agent.javassist.Modifier.SYNCHRONIZED;
                    break;
                case ABSTRACT:
                    m |= org.hotswap.agent.javassist.Modifier.ABSTRACT;
                    break;
                case PUBLIC:
                    m |= org.hotswap.agent.javassist.Modifier.PUBLIC;
                    break;
                case PROTECTED:
                    m |= org.hotswap.agent.javassist.Modifier.PROTECTED;
                    break;
                case PRIVATE:
                    m |= org.hotswap.agent.javassist.Modifier.PRIVATE;
                    break;
                case VOLATILE:
                    m |= org.hotswap.agent.javassist.Modifier.VOLATILE;
                    break;
                case TRANSIENT:
                    m |= org.hotswap.agent.javassist.Modifier.TRANSIENT;
                    break;
                case STRICT:
                    m |= org.hotswap.agent.javassist.Modifier.STRICT;
                    break;
            }
        }

        return m;
    }
}
