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
 * Plugin definition.
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Plugin {

    /**
     * A name of the plugin. This name is used to reference the plugin in code and configuration. It should not
     * contain any spaces and weird characters.
     *
     * @return A name of the plugin
     */
    String name() default "";

    /**
     * Any meaningful plugin description.
     */
    String description() default "";

    /**
     * Plugin group the plugin belongs to. Group is used to resolve fallback plugin
     *
     * @return the string
     */
    String group() default "";

    /**
     * If no other plugin matches and fallback is set to true, then use this plugin
     * @return
     */
    boolean fallback() default false;

    /**
     * Version of target framework this framework was tested with.
     */
    String[] testedVersions();

    /**
     * Version of target framework this framework should work with. It is not possible to test every possible framework
     * version for all plugins. Because the plugin is usually hooked to a stable framework structure, it should
     * for all subversions of a major version. Indicate with this property expected versions.
     */
    String[] expectedVersions() default {};

    /**
     * Split plugin definition into multiple class files. Annotations @OnClassLoadEvent and @OnResourceFileEvent will be scanned on
     * supporting class in addition to pluginClass itself.
     */
    Class<?>[] supportClass() default {};

}
