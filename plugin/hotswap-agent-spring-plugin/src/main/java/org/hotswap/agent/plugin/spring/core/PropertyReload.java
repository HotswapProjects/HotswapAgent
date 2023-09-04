package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.api.IResourcePropertySource;
import org.hotswap.agent.plugin.spring.utils.AnnotatedBeanDefinitionUtils;
import org.hotswap.agent.plugin.spring.utils.ConstructorUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashSet;
import java.util.Set;

public class PropertyReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertyReload.class);


    public static void reloadPropertySource(DefaultListableBeanFactory beanFactory) {
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);
        if (environment != null) {
            for (PropertySource<?> propertySource : environment.getPropertySources()) {
                if (propertySource instanceof IResourcePropertySource) {
                    try {
                        ((IResourcePropertySource) propertySource).reloadPropertySource();
                    } catch (IOException e) {
                        LOGGER.error("reload property source error", e, propertySource.getName());
                    }
                }
            }
        }

        String[] beanFactoryBeanNamesForTypes = beanFactory.getBeanNamesForType(PlaceholderConfigurerSupport.class);
        if (beanFactoryBeanNamesForTypes != null) {
            for (String beanFactoryBeanName : beanFactoryBeanNamesForTypes) {
                PlaceholderConfigurerSupport placeholderConfigurerSupport = beanFactory.getBean(beanFactoryBeanName, PlaceholderConfigurerSupport.class);
                try {
                    updatePlaceholderConfigurerSupport(beanFactory, placeholderConfigurerSupport);
                } catch (Exception e) {
                }
            }
        }
    }


    private static void updatePlaceholderConfigurerSupport(DefaultListableBeanFactory beanFactory, PlaceholderConfigurerSupport placeholderConfigurerSupport) {
        if (placeholderConfigurerSupport instanceof PropertySourcesPlaceholderConfigurer) {
            PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = (PropertySourcesPlaceholderConfigurer) placeholderConfigurerSupport;
            Field field = null;
            try {
                field = PropertySourcesPlaceholderConfigurer.class.getDeclaredField("propertySources");
                field.setAccessible(true);
                MutablePropertySources origPropertySources = (MutablePropertySources) field.get(propertySourcesPlaceholderConfigurer);
                origPropertySources.forEach(propertySource -> {
                    origPropertySources.remove(propertySource.getName());
                });
                field.set(propertySourcesPlaceholderConfigurer, null);
                propertySourcesPlaceholderConfigurer.postProcessBeanFactory(beanFactory);

                MutablePropertySources curPropertySources = (MutablePropertySources) field.get(propertySourcesPlaceholderConfigurer);
                curPropertySources.forEach(propertySource -> {
                    origPropertySources.addLast(propertySource);
                });
                field.set(propertySourcesPlaceholderConfigurer, origPropertySources);
            } catch (NoSuchFieldException e) {
                LOGGER.debug("Failed to reload PropertySourcesPlaceholderConfigurer, possibly Spring version is 3.x or less", e);
            } catch (IllegalAccessException e) {
                LOGGER.debug("Failed to reload PropertySourcesPlaceholderConfigurer, possibly Spring version is 3.x or less", e);
            }
        } else if (placeholderConfigurerSupport instanceof PropertyPlaceholderConfigurer) {
            PropertyPlaceholderConfigurer propertyPlaceholderConfigurer = (PropertyPlaceholderConfigurer) placeholderConfigurerSupport;
            propertyPlaceholderConfigurer.postProcessBeanFactory(beanFactory);
        }
    }

    /**
     * Deal with the condition:
     * 1. constructor contains @Value parameter
     * 2. @Bean method contains @Value parameter
     *
     * @param beanFactory
     * @return
     */
    public static Set<String> checkAndGetReloadBeanNames(DefaultListableBeanFactory beanFactory) {
        Set<String> recreatedBeans = new HashSet<>();
        // resolve constructor arguments
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition instanceof AnnotatedBeanDefinition) {
                if (beanDefinition instanceof RootBeanDefinition) {
                    RootBeanDefinition currentBeanDefinition = (RootBeanDefinition) beanDefinition;
                    if (currentBeanDefinition.getFactoryMethodName() != null && currentBeanDefinition.getFactoryBeanName() != null) {
                        Method method = currentBeanDefinition.getResolvedFactoryMethod();
                        if (method == null) {
                            Object factoryBean = beanFactory.getBean(currentBeanDefinition.getFactoryBeanName());
                            Class factoryClass = ClassUtils.getUserClass(factoryBean.getClass());
                            Method[] methods = ConstructorUtils.getCandidateMethods(factoryClass, currentBeanDefinition);
                            for (Method m : methods) {
                                if (!Modifier.isStatic(m.getModifiers()) && currentBeanDefinition.isFactoryMethod(m) &&
                                        m.getParameterCount() != 0 && containValueAnnotation(m.getParameterAnnotations())) {
                                    recreatedBeans.add(beanName);
                                    break;
                                }
                            }
                        } else if (method.getParameterCount() != 0) {
                            // @Bean method contains @Value parameter
                            if (containValueAnnotation(method.getParameterAnnotations())) {
                                recreatedBeans.add(beanName);
                            }
                        }
                    }
                } else if (beanDefinition instanceof GenericBeanDefinition) {
                    GenericBeanDefinition currentBeanDefinition = (GenericBeanDefinition) beanDefinition;
                    if (BeanFactoryProcessor.checkNeedReload(beanFactory, currentBeanDefinition, beanName, constructors -> {
                        for (Constructor constructor : constructors) {
                            if (constructor.getParameterCount() != 0 && containValueAnnotation(constructor.getParameterAnnotations())) {
                                return true;
                            }
                        }
                        return false;
                    })) {
                        recreatedBeans.add(beanName);
                    }
                    // fixme factory method
                } else if (beanDefinition instanceof AnnotatedGenericBeanDefinition) {
                    AnnotatedGenericBeanDefinition currentBeanDefinition = (AnnotatedGenericBeanDefinition) beanDefinition;
                    Method resolveBeanClassMethod = ReflectionUtils.findMethod(beanFactory.getClass(), "resolveBeanClass", RootBeanDefinition.class, String.class, Class[].class);
                    if (AnnotatedBeanDefinitionUtils.getFactoryMethodMetadata(currentBeanDefinition) == null && resolveBeanClassMethod != null) {
                        resolveBeanClassMethod.setAccessible(true);
                        Class<?> beanClass = null;
                        BeanDefinition rBeanDefinition = beanFactory.getMergedBeanDefinition(beanName);
                        try {
                            if (rBeanDefinition != null) {
                                beanClass = (Class<?>) resolveBeanClassMethod.invoke(beanFactory, rBeanDefinition, beanName, new Class[]{});
                            }
                        } catch (IllegalAccessException e) {
                            LOGGER.warning("resolveBeanClass error", e);
                            continue;
                        } catch (InvocationTargetException e) {
                            LOGGER.warning("resolveBeanClass error", e);
                            continue;
                        } catch (Exception e) {
                            LOGGER.warning("resolveBeanClass error", e);
                            continue;
                        }
                        Method method = ReflectionUtils.findMethod(beanFactory.getClass(), "determineConstructorsFromBeanPostProcessors", Class.class, String.class);
                        if (method != null) {
                            method.setAccessible(true);
                            try {
                                Constructor<?>[] constructors = (Constructor<?>[]) method.invoke(beanFactory, beanClass, beanName);
                                if (constructors != null && constructors.length > 0) {
                                    if (containValueAnnotation(constructors[0].getParameterAnnotations())) {
                                        recreatedBeans.add(beanName);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("determineConstructorsFromBeanPostProcessors error", e);
                            }
                        }
                    }
                    // fixme factory method
                }
            }
        }
        return recreatedBeans;
    }

    private static boolean containValueAnnotation(Annotation[][] annotations) {
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
