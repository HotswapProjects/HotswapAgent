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
package org.hotswap.agent.util.classloader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

public class ClassLoaderHelperTest {
    @Test
    public void testIsClassLoaded() throws Exception {
        ClassLoader testClassLoader = new URLClassLoader(new URL[] {});

        String className = "org.hotswap.agent.testData.SimplePlugin";
        assertFalse("Class not loaded", ClassLoaderHelper.isClassLoaded(testClassLoader, className));

        Class.forName(className, true, testClassLoader);

        assertTrue("Class loaded", ClassLoaderHelper.isClassLoaded(testClassLoader, className));

    }
}