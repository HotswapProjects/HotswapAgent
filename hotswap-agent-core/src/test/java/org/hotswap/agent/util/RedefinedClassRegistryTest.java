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
package org.hotswap.agent.util;

import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RedefinedClassRegistryTest {

    public static class Loaded {
    }

    private static final String LOADED = Loaded.class.getName();
    private static final byte[] V1 = {1, 2, 3};
    private static final byte[] V2 = {1, 2, 4};

    private final RedefinedClassRegistry registry = new RedefinedClassRegistry();

    @Before
    public void before() {
        registry.setEnabled(true);
    }

    @Test
    public void unknownClassIsNotCurrent() {
        assertFalse(registry.isCurrent(String.class, V1));
    }

    @Test
    public void classLoadedFromDirectoryIsCurrent() throws Exception {
        try (URLClassLoader jarLoader = isolatedClassLoader()) {
            registry.loaded(jarLoader, internalName(LOADED), protectionDomain("file:/app/lib/app.jar"), V1);
            assertFalse("not recorded from a jar", registry.isCurrent(jarLoader.loadClass(LOADED), V1));
        }

        try (URLClassLoader classLoader = isolatedClassLoader()) {
            registry.loaded(classLoader, internalName(LOADED), protectionDomain("file:/app/target/classes/"), V1);
            Class<?> clazz = classLoader.loadClass(LOADED);
            assertTrue(registry.isCurrent(clazz, V1));
            assertFalse(registry.isCurrent(clazz, V2));
        }
    }

    @Test
    public void retriedDefinitionIsRecorded() throws Exception {
        try (URLClassLoader classLoader = isolatedClassLoader()) {
            // e.g. the first definition failed with a NoClassDefFoundError of a missing superclass
            registry.loaded(classLoader, internalName(LOADED), protectionDomain("file:/app/target/classes/"), V1);
            registry.loaded(classLoader, internalName(LOADED), protectionDomain("file:/app/target/classes/"), V2);
            Class<?> clazz = classLoader.loadClass(LOADED);

            assertTrue(registry.isCurrent(clazz, V2));
            assertFalse(registry.isCurrent(clazz, V1));
        }
    }

    @Test
    public void redefinedBytecodeIsCurrent() {
        redefine(V1);

        assertTrue(registry.isCurrent(String.class, V1.clone()));
        assertFalse(registry.isCurrent(String.class, V2));
        assertFalse(registry.isCurrent(Integer.class, V1));
    }

    @Test
    public void redefiningWithSameBytecodeKeepsClass() {
        redefine(V1);

        registry.redefining(String.class, V1.clone());

        assertTrue(registry.isCurrent(String.class, V1));
    }

    @Test
    public void redefiningWithOtherBytecodeForgetsClass() {
        redefine(V1);

        // e.g. a debugger redefines the class with V2, then the class file is rewritten with V1 again, or
        // a redefinition by the agent with V2 fails after the transformer saw it
        registry.redefining(String.class, V2);

        assertFalse(registry.isCurrent(String.class, V1));
        assertFalse(registry.isCurrent(String.class, V2));
    }

    @Test
    public void nothingIsRecordedWhenDisabled() throws Exception {
        registry.setEnabled(false);
        try (URLClassLoader classLoader = isolatedClassLoader()) {
            registry.loaded(classLoader, internalName(LOADED), protectionDomain("file:/app/target/classes/"), V1);
            redefine(V1);

            assertFalse(registry.isCurrent(classLoader.loadClass(LOADED), V1));
            assertFalse(registry.isCurrent(String.class, V1));
        }
    }

    private void redefine(byte[] bytecode) {
        registry.redefined(Collections.singletonMap(String.class, bytecode));
    }

    // defines Loaded only when asked, so that the class is not loaded yet when loaded() is called
    private static URLClassLoader isolatedClassLoader() {
        return new URLClassLoader(new URL[]{Loaded.class.getProtectionDomain().getCodeSource().getLocation()}, null);
    }

    private static String internalName(String className) {
        return className.replace('.', '/');
    }

    private static ProtectionDomain protectionDomain(String location) throws Exception {
        return new ProtectionDomain(new CodeSource(new URL(location), (Certificate[]) null), null);
    }
}
