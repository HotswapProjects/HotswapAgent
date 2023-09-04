package org.hotswap.agent.plugin.spring.utils;

import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;

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
}
