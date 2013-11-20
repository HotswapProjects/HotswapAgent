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

/**
 * Simple translator of method bodies
 * (also see the <code>javassist.expr</code> package).
 * <p/>
 * <p>Instances of this class specifies how to instrument of the
 * bytecodes representing a method body.  They are passed to
 * <code>CtClass.instrument()</code> or
 * <code>CtMethod.instrument()</code> as a parameter.
 * <p/>
 * <p>Example:
 * <ul><pre>
 * ClassPool cp = ClassPool.getDefault();
 * CtClass point = cp.get("Point");
 * CtClass singleton = cp.get("Singleton");
 * CtClass client = cp.get("Client");
 * CodeConverter conv = new CodeConverter();
 * conv.replaceNew(point, singleton, "makePoint");
 * client.instrument(conv);
 * </pre></ul>
 * <p/>
 * <p>This program substitutes "<code>Singleton.makePoint()</code>"
 * for all occurrences of "<code>new Point()</code>"
 * appearing in methods declared in a <code>Client</code> class.
 *
 * @see CtClass#instrument(CodeConverter)
 * @see CtMethod#instrument(CodeConverter)
 * @see org.hotswap.agent.javassist.expr.ExprEditor
 */
public class CodeConverter {
    protected org.hotswap.agent.javassist.convert.Transformer transformers = null;

    /**
     * Modify a method body so that instantiation of the specified class
     * is replaced with a call to the specified static method.  For example,
     * <code>replaceNew(ctPoint, ctSingleton, "createPoint")</code>
     * (where <code>ctPoint</code> and <code>ctSingleton</code> are
     * compile-time classes for class <code>Point</code> and class
     * <code>Singleton</code>, respectively)
     * replaces all occurrences of:
     * <p/>
     * <ul><code>new Point(x, y)</code></ul>
     * <p/>
     * in the method body with:
     * <p/>
     * <ul><code>Singleton.createPoint(x, y)</code></ul>
     * <p/>
     * <p>This enables to intercept instantiation of <code>Point</code>
     * and change the samentics.  For example, the following
     * <code>createPoint()</code> implements the singleton pattern:
     * <p/>
     * <ul><pre>public static Point createPoint(int x, int y) {
     *     if (aPoint == null)
     *         aPoint = new Point(x, y);
     *     return aPoint;
     * }
     * </pre></ul>
     * <p/>
     * <p>The static method call substituted for the original <code>new</code>
     * expression must be
     * able to receive the same set of parameters as the original
     * constructor.  If there are multiple constructors with different
     * parameter types, then there must be multiple static methods
     * with the same name but different parameter types.
     * <p/>
     * <p>The return type of the substituted static method must be
     * the exactly same as the type of the instantiated class specified by
     * <code>newClass</code>.
     *
     * @param newClass     the instantiated class.
     * @param calledClass  the class in which the static method is
     *                     declared.
     * @param calledMethod the name of the static method.
     */
    public void replaceNew(CtClass newClass,
                           CtClass calledClass, String calledMethod) {
        transformers = new org.hotswap.agent.javassist.convert.TransformNew(transformers, newClass.getName(),
                calledClass.getName(), calledMethod);
    }

    /**
     * Modify a method body so that instantiation of the class
     * specified by <code>oldClass</code>
     * is replaced with instantiation of another class <code>newClass</code>.
     * For example,
     * <code>replaceNew(ctPoint, ctPoint2)</code>
     * (where <code>ctPoint</code> and <code>ctPoint2</code> are
     * compile-time classes for class <code>Point</code> and class
     * <code>Point2</code>, respectively)
     * replaces all occurrences of:
     * <p/>
     * <ul><code>new Point(x, y)</code></ul>
     * <p/>
     * in the method body with:
     * <p/>
     * <ul><code>new Point2(x, y)</code></ul>
     * <p/>
     * <p>Note that <code>Point2</code> must be type-compatible with <code>Point</code>.
     * It must have the same set of methods, fields, and constructors as the
     * replaced class.
     */
    public void replaceNew(CtClass oldClass, CtClass newClass) {
        transformers = new org.hotswap.agent.javassist.convert.TransformNewClass(transformers, oldClass.getName(),
                newClass.getName());
    }

    /**
     * Modify a method body so that field read/write expressions access
     * a different field from the original one.
     * <p/>
     * <p>Note that this method changes only the filed name and the class
     * declaring the field; the type of the target object does not change.
     * Therefore, the substituted field must be declared in the same class
     * or a superclass of the original class.
     * <p/>
     * <p>Also, <code>clazz</code> and <code>newClass</code> must specify
     * the class directly declaring the field.  They must not specify
     * a subclass of that class.
     *
     * @param field        the originally accessed field.
     * @param newClass     the class declaring the substituted field.
     * @param newFieldname the name of the substituted field.
     */
    public void redirectFieldAccess(CtField field,
                                    CtClass newClass, String newFieldname) {
        transformers = new org.hotswap.agent.javassist.convert.TransformFieldAccess(transformers, field,
                newClass.getName(),
                newFieldname);
    }

    /**
     * Modify a method body so that an expression reading the specified
     * field is replaced with a call to the specified <i>static</i> method.
     * This static method receives the target object of the original
     * read expression as a parameter.  It must return a value of
     * the same type as the field.
     * <p/>
     * <p>For example, the program below
     * <p/>
     * <ul><pre>Point p = new Point();
     * int newX = p.x + 3;</pre></ul>
     * <p/>
     * <p>can be translated into:
     * <p/>
     * <ul><pre>Point p = new Point();
     * int newX = Accessor.readX(p) + 3;</pre></ul>
     * <p/>
     * <p>where
     * <p/>
     * <ul><pre>public class Accessor {
     *     public static int readX(Object target) { ... }
     * }</pre></ul>
     * <p/>
     * <p>The type of the parameter of <code>readX()</code> must
     * be <code>java.lang.Object</code> independently of the actual
     * type of <code>target</code>.  The return type must be the same
     * as the field type.
     *
     * @param field        the field.
     * @param calledClass  the class in which the static method is
     *                     declared.
     * @param calledMethod the name of the static method.
     */
    public void replaceFieldRead(CtField field,
                                 CtClass calledClass, String calledMethod) {
        transformers = new org.hotswap.agent.javassist.convert.TransformReadField(transformers, field,
                calledClass.getName(),
                calledMethod);
    }

    /**
     * Modify a method body so that an expression writing the specified
     * field is replaced with a call to the specified static method.
     * This static method receives two parameters: the target object of
     * the original
     * write expression and the assigned value.  The return type of the
     * static method is <code>void</code>.
     * <p/>
     * <p>For example, the program below
     * <p/>
     * <ul><pre>Point p = new Point();
     * p.x = 3;</pre></ul>
     * <p/>
     * <p>can be translated into:
     * <p/>
     * <ul><pre>Point p = new Point();
     * Accessor.writeX(3);</pre></ul>
     * <p/>
     * <p>where
     * <p/>
     * <ul><pre>public class Accessor {
     *     public static void writeX(Object target, int value) { ... }
     * }</pre></ul>
     * <p/>
     * <p>The type of the first parameter of <code>writeX()</code> must
     * be <code>java.lang.Object</code> independently of the actual
     * type of <code>target</code>.  The type of the second parameter
     * is the same as the field type.
     *
     * @param field        the field.
     * @param calledClass  the class in which the static method is
     *                     declared.
     * @param calledMethod the name of the static method.
     */
    public void replaceFieldWrite(CtField field,
                                  CtClass calledClass, String calledMethod) {
        transformers = new org.hotswap.agent.javassist.convert.TransformWriteField(transformers, field,
                calledClass.getName(),
                calledMethod);
    }

    /**
     * Modify a method body, so that ALL accesses to an array are replaced with
     * calls to static methods within another class. In the case of reading an
     * element from the array, this is replaced with a call to a static method with
     * the array and the index as arguments, the return value is the value read from
     * the array. If writing to an array, this is replaced with a call to a static
     * method with the array, index and new value as parameters, the return value of
     * the static method is <code>void</code>.
     * <p/>
     * <p>The <code>calledClass</code> parameter is the class containing the static methods to be used
     * for array replacement. The <code>names</code> parameter points to an implementation of
     * <code>ArrayAccessReplacementMethodNames</code> which specifies the names of the method to be
     * used for access for each type of array.  For example reading from an <code>int[]</code> will
     * require a different method than if writing to an <code>int[]</code>, and writing to a <code>long[]</code>
     * will require a different method than if writing to a <code>byte[]</code>. If the implementation
     * of <code>ArrayAccessReplacementMethodNames</code> does not contain the name for access for a
     * type of array, that access is not replaced.
     * <p/>
     * <p>A default implementation of <code>ArrayAccessReplacementMethodNames</code> called
     * <code>DefaultArrayAccessReplacementMethodNames</code> has been provided and is what is used in the
     * following example. This also assumes that <code>'foo.ArrayAdvisor'</code> is the name of the
     * <code>CtClass</code> passed in.
     * <p/>
     * <p>If we have the following class:
     * <pre>class POJO{
     *    int[] ints = new int[]{1, 2, 3, 4, 5};
     *    long[] longs = new int[]{10, 20, 30};
     *    Object objects = new Object[]{true, false};
     *    Integer[] integers = new Integer[]{new Integer(10)};
     * }
     * </pre>
     * and this is accessed as:
     * <pre>POJO p = new POJO();
     * <p/>
     * //Write to int array
     * p.ints[2] = 7;
     * <p/>
     * //Read from int array
     * int i = p.ints[2];
     * <p/>
     * //Write to long array
     * p.longs[2] = 1000L;
     * <p/>
     * //Read from long array
     * long l = p.longs[2];
     * <p/>
     * //Write to Object array
     * p.objects[2] = "Hello";
     * <p/>
     * //Read from Object array
     * Object o = p.objects[2];
     * <p/>
     * //Write to Integer array
     * Integer integer = new Integer(5);
     * p.integers[0] = integer;
     * <p/>
     * //Read from Object array
     * integer = p.integers[0];
     * </pre>
     * <p/>
     * Following instrumentation we will have
     * <pre>POJO p = new POJO();
     * <p/>
     * //Write to int array
     * ArrayAdvisor.arrayWriteInt(p.ints, 2, 7);
     * <p/>
     * //Read from int array
     * int i = ArrayAdvisor.arrayReadInt(p.ints, 2);
     * <p/>
     * //Write to long array
     * ArrayAdvisor.arrayWriteLong(p.longs, 2, 1000L);
     * <p/>
     * //Read from long array
     * long l = ArrayAdvisor.arrayReadLong(p.longs, 2);
     * <p/>
     * //Write to Object array
     * ArrayAdvisor.arrayWriteObject(p.objects, 2, "Hello");
     * <p/>
     * //Read from Object array
     * Object o = ArrayAdvisor.arrayReadObject(p.objects, 2);
     * <p/>
     * //Write to Integer array
     * Integer integer = new Integer(5);
     * ArrayAdvisor.arrayWriteObject(p.integers, 0, integer);
     * <p/>
     * //Read from Object array
     * integer = ArrayAdvisor.arrayWriteObject(p.integers, 0);
     * </pre>
     *
     * @param calledClass the class containing the static methods.
     * @param names       contains the names of the methods to replace
     *                    the different kinds of array access with.
     * @see DefaultArrayAccessReplacementMethodNames
     */
    public void replaceArrayAccess(CtClass calledClass, ArrayAccessReplacementMethodNames names)
            throws NotFoundException {
        transformers = new org.hotswap.agent.javassist.convert.TransformAccessArrayField(transformers, calledClass.getName(), names);
    }

    /**
     * Modify method invocations in a method body so that a different
     * method will be invoked.
     * <p/>
     * <p>Note that the target object, the parameters, or
     * the type of invocation
     * (static method call, interface call, or private method call)
     * are not modified.  Only the method name is changed.  The substituted
     * method must have the same signature that the original one has.
     * If the original method is a static method, the substituted method
     * must be static.
     *
     * @param origMethod  original method
     * @param substMethod substituted method
     */
    public void redirectMethodCall(CtMethod origMethod,
                                   CtMethod substMethod)
            throws CannotCompileException {
        String d1 = origMethod.getMethodInfo2().getDescriptor();
        String d2 = substMethod.getMethodInfo2().getDescriptor();
        if (!d1.equals(d2))
            throw new CannotCompileException("signature mismatch: "
                    + substMethod.getLongName());

        int mod1 = origMethod.getModifiers();
        int mod2 = substMethod.getModifiers();
        if (Modifier.isStatic(mod1) != Modifier.isStatic(mod2)
                || (Modifier.isPrivate(mod1) && !Modifier.isPrivate(mod2))
                || origMethod.getDeclaringClass().isInterface()
                != substMethod.getDeclaringClass().isInterface())
            throw new CannotCompileException("invoke-type mismatch "
                    + substMethod.getLongName());

        transformers = new org.hotswap.agent.javassist.convert.TransformCall(transformers, origMethod,
                substMethod);
    }

    /**
     * Correct invocations to a method that has been renamed.
     * If a method is renamed, calls to that method must be also
     * modified so that the method with the new name will be called.
     * <p/>
     * <p>The method must be declared in the same class before and
     * after it is renamed.
     * <p/>
     * <p>Note that the target object, the parameters, or
     * the type of invocation
     * (static method call, interface call, or private method call)
     * are not modified.  Only the method name is changed.
     *
     * @param oldMethodName the old name of the method.
     * @param newMethod     the method with the new name.
     * @see CtMethod#setName(String)
     */
    public void redirectMethodCall(String oldMethodName,
                                   CtMethod newMethod)
            throws CannotCompileException {
        transformers
                = new org.hotswap.agent.javassist.convert.TransformCall(transformers, oldMethodName, newMethod);
    }

    /**
     * Insert a call to another method before an existing method call.
     * That "before" method must be static.  The return type must be
     * <code>void</code>.  As parameters, the before method receives
     * the target object and all the parameters to the originally invoked
     * method.  For example, if the originally invoked method is
     * <code>move()</code>:
     * <p/>
     * <ul><pre>class Point {
     *     Point move(int x, int y) { ... }
     * }</pre></ul>
     * <p/>
     * <p>Then the before method must be something like this:
     * <p/>
     * <ul><pre>class Verbose {
     *     static void print(Point target, int x, int y) { ... }
     * }</pre></ul>
     * <p/>
     * <p>The <code>CodeConverter</code> would translate bytecode
     * equivalent to:
     * <p/>
     * <ul><pre>Point p2 = p.move(x + y, 0);</pre></ul>
     * <p/>
     * <p>into the bytecode equivalent to:
     * <p/>
     * <ul><pre>int tmp1 = x + y;
     * int tmp2 = 0;
     * Verbose.print(p, tmp1, tmp2);
     * Point p2 = p.move(tmp1, tmp2);</pre></ul>
     *
     * @param origMethod   the method originally invoked.
     * @param beforeMethod the method invoked before
     *                     <code>origMethod</code>.
     */
    public void insertBeforeMethod(CtMethod origMethod,
                                   CtMethod beforeMethod)
            throws CannotCompileException {
        try {
            transformers = new org.hotswap.agent.javassist.convert.TransformBefore(transformers, origMethod,
                    beforeMethod);
        } catch (NotFoundException e) {
            throw new CannotCompileException(e);
        }
    }

    /**
     * Inserts a call to another method after an existing method call.
     * That "after" method must be static.  The return type must be
     * <code>void</code>.  As parameters, the after method receives
     * the target object and all the parameters to the originally invoked
     * method.  For example, if the originally invoked method is
     * <code>move()</code>:
     * <p/>
     * <ul><pre>class Point {
     *     Point move(int x, int y) { ... }
     * }</pre></ul>
     * <p/>
     * <p>Then the after method must be something like this:
     * <p/>
     * <ul><pre>class Verbose {
     *     static void print(Point target, int x, int y) { ... }
     * }</pre></ul>
     * <p/>
     * <p>The <code>CodeConverter</code> would translate bytecode
     * equivalent to:
     * <p/>
     * <ul><pre>Point p2 = p.move(x + y, 0);</pre></ul>
     * <p/>
     * <p>into the bytecode equivalent to:
     * <p/>
     * <ul><pre>int tmp1 = x + y;
     * int tmp2 = 0;
     * Point p2 = p.move(tmp1, tmp2);
     * Verbose.print(p, tmp1, tmp2);</pre></ul>
     *
     * @param origMethod  the method originally invoked.
     * @param afterMethod the method invoked after
     *                    <code>origMethod</code>.
     */
    public void insertAfterMethod(CtMethod origMethod,
                                  CtMethod afterMethod)
            throws CannotCompileException {
        try {
            transformers = new org.hotswap.agent.javassist.convert.TransformAfter(transformers, origMethod,
                    afterMethod);
        } catch (NotFoundException e) {
            throw new CannotCompileException(e);
        }
    }

    /**
     * Performs code conversion.
     */
    protected void doit(CtClass clazz, org.hotswap.agent.javassist.bytecode.MethodInfo minfo, org.hotswap.agent.javassist.bytecode.ConstPool cp)
            throws CannotCompileException {
        org.hotswap.agent.javassist.convert.Transformer t;
        org.hotswap.agent.javassist.bytecode.CodeAttribute codeAttr = minfo.getCodeAttribute();
        if (codeAttr == null || transformers == null)
            return;
        for (t = transformers; t != null; t = t.getNext())
            t.initialize(cp, clazz, minfo);

        org.hotswap.agent.javassist.bytecode.CodeIterator iterator = codeAttr.iterator();
        while (iterator.hasNext()) {
            try {
                int pos = iterator.next();
                for (t = transformers; t != null; t = t.getNext())
                    pos = t.transform(clazz, pos, iterator, cp);
            } catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
                throw new CannotCompileException(e);
            }
        }

        int locals = 0;
        int stack = 0;
        for (t = transformers; t != null; t = t.getNext()) {
            int s = t.extraLocals();
            if (s > locals)
                locals = s;

            s = t.extraStack();
            if (s > stack)
                stack = s;
        }

        for (t = transformers; t != null; t = t.getNext())
            t.clean();

        if (locals > 0)
            codeAttr.setMaxLocals(codeAttr.getMaxLocals() + locals);

        if (stack > 0)
            codeAttr.setMaxStack(codeAttr.getMaxStack() + stack);

        try {
            minfo.rebuildStackMapIf6(clazz.getClassPool(),
                    clazz.getClassFile2());
        } catch (org.hotswap.agent.javassist.bytecode.BadBytecode b) {
            throw new CannotCompileException(b.getMessage(), b);
        }
    }

    /**
     * Interface containing the method names to be used
     * as array access replacements.
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     * @version $Revision: 1.16 $
     */
    public interface ArrayAccessReplacementMethodNames {
        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)B</code> to replace reading from a byte[].
         */
        String byteOrBooleanRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;IB)V</code> to replace writing to a byte[].
         */
        String byteOrBooleanWrite();

        /**
         * @return the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)C</code> to replace reading from a char[].
         */
        String charRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;IC)V</code> to replace writing to a byte[].
         */
        String charWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)D</code> to replace reading from a double[].
         */
        String doubleRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;ID)V</code> to replace writing to a double[].
         */
        String doubleWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)F</code> to replace reading from a float[].
         */
        String floatRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;IF)V</code> to replace writing to a float[].
         */
        String floatWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)I</code> to replace reading from a int[].
         */
        String intRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;II)V</code> to replace writing to a int[].
         */
        String intWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)J</code> to replace reading from a long[].
         */
        String longRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;IJ)V</code> to replace writing to a long[].
         */
        String longWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)Ljava/lang/Object;</code>
         * to replace reading from a Object[] (or any subclass of object).
         */
        String objectRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;ILjava/lang/Object;)V</code>
         * to replace writing to a Object[] (or any subclass of object).
         */
        String objectWrite();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;I)S</code> to replace reading from a short[].
         */
        String shortRead();

        /**
         * Returns the name of a static method with the signature
         * <code>(Ljava/lang/Object;IS)V</code> to replace writing to a short[].
         */
        String shortWrite();
    }

    /**
     * Default implementation of the <code>ArrayAccessReplacementMethodNames</code>
     * interface giving default values for method names to be used for replacing
     * accesses to array elements.
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     * @version $Revision: 1.16 $
     */
    public static class DefaultArrayAccessReplacementMethodNames
            implements ArrayAccessReplacementMethodNames {
        /**
         * Returns "arrayReadByteOrBoolean" as the name of the static method with the signature
         * (Ljava/lang/Object;I)B to replace reading from a byte[].
         */
        public String byteOrBooleanRead() {
            return "arrayReadByteOrBoolean";
        }

        /**
         * Returns "arrayWriteByteOrBoolean" as the name of the static method with the signature
         * (Ljava/lang/Object;IB)V  to replace writing to a byte[].
         */
        public String byteOrBooleanWrite() {
            return "arrayWriteByteOrBoolean";
        }

        /**
         * Returns "arrayReadChar" as the name of the static method with the signature
         * (Ljava/lang/Object;I)C  to replace reading from a char[].
         */
        public String charRead() {
            return "arrayReadChar";
        }

        /**
         * Returns "arrayWriteChar" as the name of the static method with the signature
         * (Ljava/lang/Object;IC)V to replace writing to a byte[].
         */
        public String charWrite() {
            return "arrayWriteChar";
        }

        /**
         * Returns "arrayReadDouble" as the name of the static method with the signature
         * (Ljava/lang/Object;I)D to replace reading from a double[].
         */
        public String doubleRead() {
            return "arrayReadDouble";
        }

        /**
         * Returns "arrayWriteDouble" as the name of the static method with the signature
         * (Ljava/lang/Object;ID)V to replace writing to a double[].
         */
        public String doubleWrite() {
            return "arrayWriteDouble";
        }

        /**
         * Returns "arrayReadFloat" as the name of the static method with the signature
         * (Ljava/lang/Object;I)F  to replace reading from a float[].
         */
        public String floatRead() {
            return "arrayReadFloat";
        }

        /**
         * Returns "arrayWriteFloat" as the name of the static method with the signature
         * (Ljava/lang/Object;IF)V  to replace writing to a float[].
         */
        public String floatWrite() {
            return "arrayWriteFloat";
        }

        /**
         * Returns "arrayReadInt" as the name of the static method with the signature
         * (Ljava/lang/Object;I)I to replace reading from a int[].
         */
        public String intRead() {
            return "arrayReadInt";
        }

        /**
         * Returns "arrayWriteInt" as the name of the static method with the signature
         * (Ljava/lang/Object;II)V to replace writing to a int[].
         */
        public String intWrite() {
            return "arrayWriteInt";
        }

        /**
         * Returns "arrayReadLong" as the name of the static method with the signature
         * (Ljava/lang/Object;I)J to replace reading from a long[].
         */
        public String longRead() {
            return "arrayReadLong";
        }

        /**
         * Returns "arrayWriteLong" as the name of the static method with the signature
         * (Ljava/lang/Object;IJ)V to replace writing to a long[].
         */
        public String longWrite() {
            return "arrayWriteLong";
        }

        /**
         * Returns "arrayReadObject" as the name of the static method with the signature
         * (Ljava/lang/Object;I)Ljava/lang/Object;  to replace reading from a Object[] (or any subclass of object).
         */
        public String objectRead() {
            return "arrayReadObject";
        }

        /**
         * Returns "arrayWriteObject" as the name of the static method with the signature
         * (Ljava/lang/Object;ILjava/lang/Object;)V  to replace writing to a Object[] (or any subclass of object).
         */
        public String objectWrite() {
            return "arrayWriteObject";
        }

        /**
         * Returns "arrayReadShort" as the name of the static method with the signature
         * (Ljava/lang/Object;I)S to replace reading from a short[].
         */
        public String shortRead() {
            return "arrayReadShort";
        }

        /**
         * Returns "arrayWriteShort" as the name of the static method with the signature
         * (Ljava/lang/Object;IS)V to replace writing to a short[].
         */
        public String shortWrite() {
            return "arrayWriteShort";
        }
    }
}
