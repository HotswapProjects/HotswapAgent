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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Manifest matching configuration
 *
 *
  *
 * @author alpapad@gmail.com
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Manifest {

    /**
     * Include versions defined as maven version ranges
     *
     * @return
     */
    String value();

    /**
     * The Manifest Names (keys) to be used to retrieve the version. These are tried in the order they get declared
     *
     * @return
     */
    String[] versionName() default {Name.BundleVersion,Name.ImplementationVersion, Name.SpecificationVersion};

    /**
     * Excluded versions defined as maven version ranges
     * @return
     */
    String excludeVersion() default "";

    /**
     * List of Manifest Key/Value pairs to be matched against the entries of the Manifest.
     * These are evaluated as an AND condition, meaning all should match.
     *
     *
     * @return the name[]
     */
    Name[] names() default {};
}
