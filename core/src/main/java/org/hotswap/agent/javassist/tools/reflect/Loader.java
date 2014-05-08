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

/**
 * A class loader for reflection.
 * <p/>
 * <p>To run a program, say <code>MyApp</code>,
 * including a reflective class,
 * you must write a start-up program as follows:
 * <p/>
 * <ul><pre>
 * public class Main {
 *   public static void main(String[] args) throws Throwable {
 *     Loader cl
 *         = (Loader)Main.class.getClassLoader();
 *     cl.makeReflective("Person", "MyMetaobject",
 *                       "ClassMetaobject");
 *     cl.run("MyApp", args);
 *   }
 * }
 * </pre></ul>
 * <p/>
 * <p>Then run this program as follows:
 * <p/>
 * <ul><pre>% java Loader Main arg1, ...</pre></ul>
 * <p/>
 * <p>This command runs <code>Main.main()</code> with <code>arg1</code>, ...
 * and <code>Main.main()</code> runs <code>MyApp.main()</code> with
 * <code>arg1</code>, ...
 * The <code>Person</code> class is modified
 * to be a reflective class.  Method calls on a <code>Person</code>
 * object are intercepted by an instance of <code>MyMetaobject</code>.
 * <p/>
 * <p>Also, you can run <code>MyApp</code> in a slightly different way:
 * <p/>
 * <ul><pre>
 * public class Main2 {
 *   public static void main(String[] args) throws Throwable {
 *     Loader cl = new Loader();
 *     cl.makeReflective("Person", "MyMetaobject",
 *                       "ClassMetaobject");
 *     cl.run("MyApp", args);
 *   }
 * }
 * </pre></ul>
 * <p/>
 * <p>This program is run as follows:
 * <p/>
 * <ul><pre>% java Main2 arg1, ...</pre></ul>
 * <p/>
 * <p>The difference from the former one is that the class <code>Main</code>
 * is loaded by <code>Loader</code> whereas the class
 * <code>Main2</code> is not.  Thus, <code>Main</code> belongs
 * to the same name space (security domain) as <code>MyApp</code>
 * whereas <code>Main2</code> does not; <code>Main2</code> belongs
 * to the same name space as <code>Loader</code>.
 * For more details,
 * see the notes in the manual page of <code>Loader</code>.
 * <p/>
 * <p>The class <code>Main2</code> is equivalent to this class:
 * <p/>
 * <ul><pre>
 * public class Main3 {
 *   public static void main(String[] args) throws Throwable {
 *     Reflection reflection = new Reflection();
 *     Loader cl
 *         = new Loader(ClassPool.getDefault(reflection));
 *     reflection.makeReflective("Person", "MyMetaobject",
 *                               "ClassMetaobject");
 *     cl.run("MyApp", args);
 *   }
 * }
 * </pre></ul>
 * <p/>
 * <p><b>Note:</b>
 * <p/>
 * <p><code>Loader</code> does not make a class reflective
 * if that class is in a <code>java.*</code> or
 * <code>javax.*</code> pacakge because of the specifications
 * on the class loading algorithm of Java.  The JVM does not allow to
 * load such a system class with a user class loader.
 * <p/>
 * <p>To avoid this limitation, those classes should be statically
 * modified with <code>Compiler</code> and the original
 * class files should be replaced.
 *
 * @see Reflection
 * @see Compiler
 * @see org.hotswap.agent.javassist.Loader
 */
public class Loader extends org.hotswap.agent.javassist.Loader {
    protected Reflection reflection;

    /**
     * Loads a class with an instance of <code>Loader</code>
     * and calls <code>main()</code> in that class.
     *
     * @param args command line parameters.
     *             <ul>
     *             <code>args[0]</code> is the class name to be loaded.
     *             <br><code>args[1..n]</code> are parameters passed
     *             to the target <code>main()</code>.
     *             </ul>
     */
    public static void main(String[] args) throws Throwable {
        Loader cl = new Loader();
        cl.run(args);
    }

    /**
     * Constructs a new class loader.
     */
    public Loader() throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        super();
        delegateLoadingOf("Loader");

        reflection = new Reflection();
        org.hotswap.agent.javassist.ClassPool pool = org.hotswap.agent.javassist.ClassPool.getDefault();
        addTranslator(pool, reflection);
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
     * @see ClassMetaobject
     */
    public boolean makeReflective(String clazz,
                                  String metaobject, String metaclass)
            throws org.hotswap.agent.javassist.CannotCompileException, org.hotswap.agent.javassist.NotFoundException {
        return reflection.makeReflective(clazz, metaobject, metaclass);
    }
}
