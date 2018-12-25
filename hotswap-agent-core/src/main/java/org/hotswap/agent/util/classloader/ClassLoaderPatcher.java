/*
 * Copyright 2013-2019 the HotswapAgent authors.
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
package org.hotswap.agent.util.classloader;

import java.security.ProtectionDomain;

/**
 * Add agent classloader plugin classes to the application classloader.
 * <p/>
 * This is necessary to allow direct call of app classes (typically library classes like Spring and Hibernate).
 * It is not possible to call class in child classloader from parent classloader. Hence this patch redefines
 * necessary classes in child classloader itself directly.
 *
 * @author Jiri Bubnik
 */
public interface ClassLoaderPatcher {

    /**
     * Check if the classloader can be patched.
     * Typically skip synthetic classloaders.
     *
     * @param classLoader classloader to check
     * @return if true, call patch()
     */
    boolean isPatchAvailable(ClassLoader classLoader);

    /**
     * Patch the classloader.
     *
     * @param classLoaderFrom  classloader to load classes from
     * @param path             path to copy
     * @param classLoaderTo    classloader to copy classes to
     * @param protectionDomain required protection in target classloader
     */
    void patch(ClassLoader classLoaderFrom, String path, ClassLoader classLoaderTo, ProtectionDomain protectionDomain);
}
