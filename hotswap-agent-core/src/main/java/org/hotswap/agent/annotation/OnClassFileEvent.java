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

import static org.hotswap.agent.annotation.FileEvent.*;

/**
 * OnResourceFileEvent for a change on resource file representing a java class.
 * <p/>
 * Use with a non static method.
 * <p/>
 * Method attribute types:<ul>
 * <li>ClassLoader - the application classloader</li>
 * <li>String - classname - the name of the class in the internal form of fully
 * qualified class and interface names. For example, <code>"java/util/List"</code>.</li>
 * <li>ClassPool - initialized javassist classpool</li>
 * <li>CtClass - javassist CtClass created from target file</li>
 * <li>URI - URI of the watched resource</li>
 * <li>URL - URL of the watched resource</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnClassFileEvent {

    /**
     * Regexp of class name.
     */
    String classNameRegexp();

    /**
     * Filter watch event types. Default is all events (CREATE, MODIFY, DELETE).
     *
     * Be careful about assumptions on events. Some IDEs create on file compilation sequence of
     * multiple delete/create/modify events.
     */
    FileEvent[] events() default {CREATE, MODIFY, DELETE};


    /**
     * Merge multiple same watch events up to this timeout into a single watch event (useful to merge multiple MODIFY events).
     */
    public int timeout() default 50;
}
