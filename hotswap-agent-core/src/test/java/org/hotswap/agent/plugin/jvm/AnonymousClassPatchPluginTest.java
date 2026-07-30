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
package org.hotswap.agent.plugin.jvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public class AnonymousClassPatchPluginTest {

    private static final Pattern ANONYMOUS_EVENT = Pattern.compile(".*\\$\\d+");

    @Test
    public void springCglibProxyNameMatchesTheAnonymousEventFilter() {
        assertTrue(ANONYMOUS_EVENT.matcher("com.example.AppConfig$$SpringCGLIB$$0").matches());
    }

    @Test
    public void springCglibProxyDerivesABogusMainClassWithoutTheGuard() {
        String cglib = "com.example.AppConfig$$SpringCGLIB$$0";
        assertEquals("com.example.AppConfig$$SpringCGLIB$", cglib.replaceAll("\\$\\d+$", ""));
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass(cglib));
    }

    @Test
    public void treatsProxyAndLambdaClassesAsSynthetic() {
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass("com.example.AppConfig$$SpringCGLIB$$0"));
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass("com/example/AppConfig$$SpringCGLIB$$0"));
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass("com.example.AppConfig$$EnhancerBySpringCGLIB$$1a2b3c4d"));
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass("com.example.AppConfig$$FastClassBySpringCGLIB$$e9dcbb28"));
        assertTrue(AnonymousClassPatchPlugin.isSyntheticClass("com.example.Foo$$Lambda$1"));
    }

    @Test
    public void keepsRealAnonymousAndLocalClasses() {
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass("com.example.Foo$1"));
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass("com/example/Foo$1"));
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass("com.example.Foo$Bar$2"));
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass("com.example.Foo$1Local"));
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass("com.example.Foo"));
    }

    @Test
    public void nullClassNameIsNotSynthetic() {
        assertFalse(AnonymousClassPatchPlugin.isSyntheticClass(null));
    }
}
