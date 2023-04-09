/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.annotation;

import java.lang.annotation.*;

import static org.hotswap.agent.annotation.LoadEvent.DEFINE;
import static org.hotswap.agent.annotation.LoadEvent.REDEFINE;

/**
 * Define plugin callback method on class load by classloader (application class is loaded or reloaded by hotswap).
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
 * <li>LoadEvent - originating load event. If classBeingRedefined is null, this is DEFINE, otherwise REDEFINE.</li>
 * </ul>
 * <p/>
 * If registered on static method, transformation is invoked even before the plugin is initialized.
 * You need at least one static transformation method for a plugin to trigger plugin initialization.
 * <p/>
 * This event is triggered only AFTER the class is loaded by a classloader. Many frameworks like Spring or
 * Hibernate use custom classpath scanning to discover annotated classes. In this case a change cannot be triggered
 * only by @OnClassLoadEvent method (the class is never loaded) and you need to cover this case using @OnClassFileEvent
 * handler. See HibernatePlugin#newEntity() method annotated with OnClassFileEvent for an example.
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnClassLoadEvent {

    /**
     * Regexp of class name.
     */
    String classNameRegexp();

    /**
     * Specify list of events to watch for (class is loaded by the ClassLoader / redefined by hotswap mechanism).
     * By default are both DEFINE and REDEFINE events enabled.
     *
     * @return list of class load events
     */
    LoadEvent[] events() default {DEFINE, REDEFINE};

    /**
     * Anonymous classes (e.g. MyClass$1, MyClass$2, ...) are usually reloaded with main class MyClass,
     * but the transformation should be done only on the main class.
     *
     * @return false to include anonymous classes.
     */
    boolean skipAnonymous() default true;

    /**
     * Classes created at runtime are usually skipped
     *
     * @return false to include synthetic classes.
     */
    boolean skipSynthetic() default true;

}
