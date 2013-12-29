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
