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

import org.hotswap.agent.config.PluginManager;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubnik on 13.10.13.
 */
public class ClassLoaderProxyTest {
    @Test
    public void test() throws MalformedURLException, ClassNotFoundException {

        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                appClassLoader.loadClass(PluginManager.class.getName()).getClassLoader());
        ;

        ClassLoader samePathClassLoader = new URLClassLoader(new URL[]{Paths.get("j:\\pokusy\\DcevmAgent\\target\\classes\\").toUri().toURL()}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                samePathClassLoader.loadClass(PluginManager.class.getName()).getClassLoader());
        ;

    }
}
