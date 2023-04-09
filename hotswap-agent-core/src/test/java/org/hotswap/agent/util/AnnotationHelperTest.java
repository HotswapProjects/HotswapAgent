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
package org.hotswap.agent.util;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.plugin.jvm.AnonymousClassPatchPlugin;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by bubnik on 3.11.13.
 */
public class AnnotationHelperTest {
    @Test
    public void testHasAnnotationClass() throws Exception {
        assertTrue(AnnotationHelper.hasAnnotation(AnonymousClassPatchPlugin.class, "org.hotswap.agent.annotation.Plugin"));
        assertFalse(AnnotationHelper.hasAnnotation(AnonymousClassPatchPlugin.class, "xxxx"));
    }

    @Test
    public void testHasAnnotationJavassist() throws Exception {
        ClassPool ctPool = ClassPool.getDefault();
        CtClass ctClass = ctPool.getCtClass(AnonymousClassPatchPlugin.class.getName());

        assertTrue(AnnotationHelper.hasAnnotation(ctClass, "org.hotswap.agent.annotation.Plugin"));
        assertFalse(AnnotationHelper.hasAnnotation(ctClass, "xxxx"));
    }
}
