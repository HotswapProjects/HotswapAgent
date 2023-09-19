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

    public static boolean hasAnnotation(Class<?> clazz, Iterable<String> annotationClasses) {
        for (String pathAnnotation : annotationClasses) {
            if (AnnotationHelper.hasAnnotation(clazz, pathAnnotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnnotation(CtClass clazz, Iterable<String> annotationClasses) {
        for (String pathAnnotation : annotationClasses) {
            if (AnnotationHelper.hasAnnotation(clazz, pathAnnotation)) {
                return true;
            }
        }
        return false;
    }

}
