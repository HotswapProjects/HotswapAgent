package org.hotswap.agent.util;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.testData.SimpleEntity;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by bubnik on 3.11.13.
 */
public class AnnotationHelperTest {
    @Test
    public void testHasAnnotationClass() throws Exception {
        assertTrue(AnnotationHelper.hasAnnotation(SimpleEntity.class, "javax.persistence.Entity"));
        assertFalse(AnnotationHelper.hasAnnotation(SimpleEntity.class, "xxxx"));
    }

    @Test
    public void testHasAnnotationJavassist() throws Exception {
        ClassPool ctPool = ClassPool.getDefault();
        CtClass ctClass = ctPool.getCtClass(SimpleEntity.class.getName());

        assertTrue(AnnotationHelper.hasAnnotation(ctClass, "javax.persistence.Entity"));
        assertFalse(AnnotationHelper.hasAnnotation(ctClass, "xxxx"));
    }
}
