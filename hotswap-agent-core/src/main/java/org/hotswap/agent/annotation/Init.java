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

/**
 * Initialize plugin fields and methods.
 * <p/>
 * Non static fields and methods are set after the plugin instance is created and before any other method is invoked.
 * You can use this annotation to autowire agent services.
 * <p/>
 * Special use is @Init annotation on static method - then the method works as a callback after new classloader is
 * initialized in the plugin manager. @Init on static field just sets the service if applicable.
 * <p/>
 * Available method argument types:<ul>
 * <li>PluginManager - the single instance of plugin manager</li>
 * <li>Watcher - watcher service to register resource change listeners</li>
 * <li>Scheduler - schedule a command to run</li>
 * <li>HotswapTransformer - register class transformation</li>
 * <li>PluginConfiguration - access plugin configuration properties</li>
 * <li>ClassLoader - current application classloader (for static method on a field, this is the plugin classloader) </li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Init {
}
