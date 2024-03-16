/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.utils;

import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;

import java.lang.annotation.Annotation;

public class AnnotatedBeanDefinitionUtils {

    public static MethodMetadata getFactoryMethodMetadata(AnnotatedBeanDefinition beanDefinition) {
        Object target = ReflectionHelper.invokeNoException(beanDefinition, beanDefinition.getClass().getName(), beanDefinition.getClass().getClassLoader(), "getFactoryMethodMetadata", new Class<?>[]{});
        if (target != null) {
            return (MethodMetadata) target;
        }
        /** earlier than spring 4.1 */
        if (beanDefinition.getSource() != null && beanDefinition.getSource() instanceof StandardMethodMetadata) {
            StandardMethodMetadata standardMethodMetadata = (StandardMethodMetadata) beanDefinition.getSource();
            return standardMethodMetadata;
        }
        return null;
    }

    public static boolean containValueAnnotation(Annotation[][] annotations) {
        for (int i = 0; i < annotations.length; i++) {
            Annotation[] annotationArray = annotations[i];
            for (Annotation annotation : annotationArray) {
                if (annotation.annotationType().getName().equals("org.springframework.beans.factory.annotation.Value")) {
                    return true;
                }
            }
        }
        return false;
    }
}
