package org.hotswap.agent.util;

import org.hotswap.agent.javassist.CtClass;

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
        try {
            for (Object annot : clazz.getAnnotations())
                if (((Annotation) annot).annotationType().getName().equals(annotationClass))
                    return true;
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
        return false;
    }

}
