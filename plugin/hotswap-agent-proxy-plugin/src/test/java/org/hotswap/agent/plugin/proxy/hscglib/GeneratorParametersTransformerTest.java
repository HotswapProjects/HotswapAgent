/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.proxy.hscglib;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

/**
 * See https://github.com/HotswapProjects/HotswapAgent/issues/635
 */
public class GeneratorParametersTransformerTest {

    /**
     * The recorder class is copied into application class loaders. In a class loader that cannot see the agent core
     * (e.g. the servlet container class loader of Gretty) it fails in its static initializer. Redefining a class
     * there must not fail, there are just no recorded proxies.
     */
    @Test
    public void noGeneratorParamsInClassLoaderWithoutAgentCore() throws Exception {
        final String recorderName = GeneratorParametersRecorder.class.getName();
        final byte[] recorderBytes = classBytes(GeneratorParametersRecorder.class);
        ClassLoader isolated = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(recorderName)) {
                    try {
                        return defineClass(name, recorderBytes, 0, recorderBytes.length);
                    } catch (LinkageError e) {
                        // with the agent attached (as in the build), it copies the plugin classes into this loader
                        // while the first class is defined, the same as in an application
                        Class<?> copied = findLoadedClass(name);
                        if (copied != null) {
                            return copied;
                        }
                        throw e;
                    }
                }
                throw new ClassNotFoundException(name);
            }
        };

        // the copied recorder cannot be initialized there
        try {
            isolated.loadClass(recorderName).getField("generatorParams").get(null);
            throw new AssertionError("expected NoClassDefFoundError");
        } catch (NoClassDefFoundError expected) {
            assertTrue(expected.getMessage().contains("org/hotswap/agent"));
        }

        assertNull(GeneratorParametersTransformer.getGeneratorParams(isolated, "test.SomeClass"));
        // second call uses the cached result
        assertNull(GeneratorParametersTransformer.getGeneratorParams(isolated, "test.SomeClass"));
    }

    private static byte[] classBytes(Class<?> clazz) throws IOException {
        try (InputStream in = clazz.getResourceAsStream(clazz.getSimpleName() + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
