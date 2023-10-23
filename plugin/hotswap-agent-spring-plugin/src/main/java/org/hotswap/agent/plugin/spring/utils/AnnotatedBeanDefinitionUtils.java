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
