package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.api.IPlaceholderConfigurerSupport;
import org.hotswap.agent.plugin.spring.utils.AnnotatedBeanDefinitionUtils;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

public class BeanFactoryProcessor {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanFactoryProcessor.class);

    public static void removeBeanDefinition(DefaultListableBeanFactory beanFactory, String beanName) {
        resetEmbeddedValueResolvers(beanFactory, beanName);
        beanFactory.removeBeanDefinition(beanName);
    }

    public static void destroySingleton(DefaultListableBeanFactory beanFactory, String beanName) {
        // remove embeddedValueResolvers cache in PlaceholderConfigurerSupport
        resetEmbeddedValueResolvers(beanFactory, beanName);
        beanFactory.destroySingleton(beanName);
    }

    public static boolean isFactoryBean(DefaultListableBeanFactory beanFactory, String beanName, RootBeanDefinition beanDefinition) {
        Method isFactoryBeanMethod = ReflectionUtils.findMethod(beanFactory.getClass(), "isFactoryBean", String.class, RootBeanDefinition.class);
        if (isFactoryBeanMethod != null) {
            isFactoryBeanMethod.setAccessible(true);
            try {
                return (boolean) isFactoryBeanMethod.invoke(beanFactory, beanName, beanDefinition);
            } catch (IllegalAccessException e) {
                LOGGER.warning("isFactoryBean error", e);
            } catch (InvocationTargetException e) {
                LOGGER.warning("isFactoryBean error", e);
            }
        }
        if (beanName.startsWith("&")) {
            return true;
        }
        return false;
    }

    public static boolean checkNeedReload(DefaultListableBeanFactory beanFactory, AbstractBeanDefinition currentBeanDefinition,
                                          String beanName, Predicate<Constructor<?>[]> predicate) {
        Method resolveBeanClassMethod = ReflectionUtils.findMethod(beanFactory.getClass(), "resolveBeanClass", RootBeanDefinition.class, String.class, Class[].class);
        if (currentBeanDefinition instanceof AnnotatedBeanDefinition) {
            AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) currentBeanDefinition;
            if (AnnotatedBeanDefinitionUtils.getFactoryMethodMetadata(annotatedBeanDefinition) != null) {
                return false;
            }
        }
        if (resolveBeanClassMethod != null) {
            resolveBeanClassMethod.setAccessible(true);
            Class<?> beanClass = null;
            BeanDefinition rBeanDefinition = beanFactory.getMergedBeanDefinition(beanName);
            try {
                if (rBeanDefinition != null) {
                    beanClass = (Class<?>) resolveBeanClassMethod.invoke(beanFactory, rBeanDefinition, beanName, new Class[]{});
                }
            } catch (IllegalAccessException e) {
                LOGGER.warning("resolveBeanClass error", e);
                return false;
            } catch (InvocationTargetException e) {
                LOGGER.warning("resolveBeanClass error", e);
                return false;
            } catch (Exception e) {
                LOGGER.warning("resolveBeanClass error", e);
                return false;
            }
            Method method = ReflectionUtils.findMethod(beanFactory.getClass(), "determineConstructorsFromBeanPostProcessors", Class.class, String.class);
            if (method != null) {
                method.setAccessible(true);
                try {
                    Constructor<?>[] constructors = (Constructor<?>[]) method.invoke(beanFactory, beanClass, beanName);
                    if (constructors != null && constructors.length > 0) {
                        return predicate.test(constructors);
                    }
                } catch (Exception e) {
                    LOGGER.error("determineConstructorsFromBeanPostProcessors error", e);
                }
            }
        }
        return false;
    }

    private static void resetEmbeddedValueResolvers(DefaultListableBeanFactory beanFactory, String beanName) {
        Object target = beanFactory.getSingleton(beanName);
        if (target != null && target instanceof PlaceholderConfigurerSupport && target instanceof IPlaceholderConfigurerSupport) {
            IPlaceholderConfigurerSupport placeholderConfigurerSupport = (IPlaceholderConfigurerSupport) target;
            Field field = ReflectionUtils.findField(beanFactory.getClass(), "embeddedValueResolvers");
            if (field != null) {
                ReflectionUtils.makeAccessible(field);
                List<StringValueResolver> embeddedValueResolvers = (List<StringValueResolver>) ReflectionUtils.getField(field, beanFactory);
                embeddedValueResolvers.removeAll(placeholderConfigurerSupport.valueResolvers());
            }
        }
    }

    public static boolean isAllowBeanDefinitionOverriding(DefaultListableBeanFactory beanFactory) {
        Object target = ReflectionHelper.getNoException(beanFactory, beanFactory.getClass(), "allowBeanDefinitionOverriding");
        if (target == null) {
            return false;
        }
        return (boolean) target;
    }

    public static void setAllowBeanDefinitionOverriding(DefaultListableBeanFactory beanFactory, boolean allowEagerClassLoading) {
        beanFactory.setAllowBeanDefinitionOverriding(allowEagerClassLoading);
    }

    public static BeanDefinition getBeanDefinition(DefaultListableBeanFactory beanFactory, String beanName) {
        if (beanName.startsWith("&")) {
            beanName = beanName.substring(1);
        }
        return beanFactory.getBeanDefinition(beanName);
    }

}
