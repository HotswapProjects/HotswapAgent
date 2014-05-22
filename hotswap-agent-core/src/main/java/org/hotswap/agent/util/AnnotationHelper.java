package org.hotswap.agent.util;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.bytecode.AnnotationsAttribute;

import java.lang.annotation.Annotation;

/**
 * @author Jiri Bubnik
 */
public class AnnotationHelper {
    public static boolean hasAnnotation(Class clazz, String annotationClass) {
        for (Annotation annot : clazz.getDeclaredAnnotations())
            if (annot.annotationType().getName().equals(annotationClass))
                return true;
        return false;
    }

    public static boolean hasAnnotation(CtClass clazz, String annotationClass) {
        AnnotationsAttribute ainfo = (AnnotationsAttribute) clazz.getClassFile2().
                getAttribute(AnnotationsAttribute.visibleTag);
        if (ainfo != null) {
            for (org.hotswap.agent.javassist.bytecode.annotation.Annotation annot : ainfo.getAnnotations()) {
                if (annot.getTypeName().equals(annotationClass))
                    return true;
            }
        }
        return false;
    }

}
