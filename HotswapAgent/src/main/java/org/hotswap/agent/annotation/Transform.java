package org.hotswap.agent.annotation;

import java.lang.annotation.*;

/**
 * Define plugin callback method on hotswap (application class is loaded or reloaded).
 * <p/>
 * Method attribute types:<ul>
 * <li>byte[] - the input byte buffer in class file format - must not be modified</li>
 * <li>ClassLoader - the defining loader of the class to be transformed,
 * may be <code>null</code> if the bootstrap loader</li>
 * <li>String - classname - the name of the class in the internal form of fully
 * qualified class and interface names. For example, <code>"java/util/List"</code>.</li>
 * <li>Class - classBeingRedefined - if this is triggered by a redefine or retransform,
 * the class being redefined or retransformed; if this is a class load, <code>null</code></li>
 * <li>ProtectionDomain - the protection domain of the class being defined or redefined</li>
 * <li>ClassPool - javassist default ClassPool</li>
 * <li>CtClass - javassist class created from byte[] source. If the method returns null/void,
 * this class is used as transformation result. You can modify this class directly.</li>
 * <li>AppClassLoaderExecutor - executor to run code in app classloader</li>
 * </ul>
 * <p/>
 * If registered on static method, transformation is invoked even before the plugin is initialized.
 * You need at least one static transformation method for a plugin to trigger plugin initialization.
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Transform {

    /**
     * Regexp of class name.
     */
    String classNameRegexp();

    /**
     * Should the transformation be done for first instantiation?
     *
     * @return true to transform new version
     */
    boolean onDefine() default true;

    /**
     * Should the transformation be done for hotswap reload?
     *
     * @return true to transform hotswap reloaded version
     */
    boolean onReload() default true;

    /**
     * Anonymous classes (e.g. MyClass$1, MyClass$2, ...) are usually reloaded with main class MyClass,
     * but the transformation should be done only on the main class.
     *
     * @return false to include anonymous classes.
     */
    boolean skipAnonymous() default true;

}
