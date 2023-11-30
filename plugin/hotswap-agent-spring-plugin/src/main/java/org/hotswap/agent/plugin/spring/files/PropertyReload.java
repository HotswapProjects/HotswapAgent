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
package org.hotswap.agent.plugin.spring.files;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.BeanFactoryProcessor;
import org.hotswap.agent.plugin.spring.listener.SpringEventSource;
import org.hotswap.agent.plugin.spring.transformers.api.ReloadablePropertySource;
import org.hotswap.agent.plugin.spring.transformers.api.ReloadableResourcePropertySource;
import org.hotswap.agent.plugin.spring.utils.AnnotatedBeanDefinitionUtils;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.function.Consumer;

public class PropertyReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertyReload.class);


    public static void reloadPropertySource(DefaultListableBeanFactory beanFactory) {
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);
        if (environment != null) {
            Map<String, String> oldValueMap = getPropertyOfPropertySource(environment);
            // reload
            doReloadPropertySource(environment.getPropertySources());
            // compare the old value and new value, and fire changed event
            processChangedValue(beanFactory, environment, oldValueMap);
        }

        refreshPlaceholderConfigurerSupport(beanFactory);
    }

    private static Map<String, String> getPropertyOfPropertySource(ConfigurableEnvironment environment) {
        Set<String> canModifiedKey = new HashSet<>();
        Map<String, String> result = new HashMap<>();
        // fetch the keys of modified property source
        processKeysOfPropertySource(environment.getPropertySources(), canModifiedKey::addAll);
        // fetch the old value of modified property source
        for (String key : canModifiedKey) {
            result.put(key, environment.getProperty(key));
        }
        return result;
    }

    private static void processChangedValue(DefaultListableBeanFactory beanFactory, ConfigurableEnvironment environment,
                                            Map<String, String> oldValueMap) {
        Set<String> canModifiedKey = new HashSet<>();
        processKeysOfPropertySource(environment.getPropertySources(), canModifiedKey::addAll);

        List<PropertiesChangeEvent.PropertyChangeItem> propertyChangeItems = new ArrayList<>();
        for (String key : canModifiedKey) {
            String oldValue = oldValueMap.get(key);
            String newValue = environment.getProperty(key);
            if ((oldValue != null && !oldValue.equals(newValue)) || (oldValue == null && newValue != null)) {
                propertyChangeItems.add(new PropertiesChangeEvent.PropertyChangeItem(key, oldValue, newValue));
                LOGGER.debug("property of '{}' reload, key:{}, oldValue:{}, newValue:{}",
                        ObjectUtils.identityToString(beanFactory), key, oldValue, newValue);
            }
        }
        if (!propertyChangeItems.isEmpty()) {
            SpringEventSource.INSTANCE.fireEvent(new PropertiesChangeEvent(propertyChangeItems, beanFactory));
        }
    }

    private static void processKeysOfPropertySource(MutablePropertySources propertySources, Consumer<Set<String>> consumer) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof MapPropertySource) {
                consumer.accept(((MapPropertySource) propertySource).getSource().keySet());
            }
        }
    }


    private static void doReloadPropertySource(MutablePropertySources propertySources) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof ReloadableResourcePropertySource) {
                try {
                    ((ReloadableResourcePropertySource) propertySource).reload();
                } catch (IOException e) {
                    LOGGER.error("reload property source error", e, propertySource.getName());
                }
            }
            if (propertySource instanceof ReloadablePropertySource) {
                ((ReloadablePropertySource) propertySource).reload();
            }
        }
    }

    private static void refreshPlaceholderConfigurerSupport(DefaultListableBeanFactory beanFactory) {
        String[] beanFactoryBeanNamesForTypes = beanFactory.getBeanNamesForType(PlaceholderConfigurerSupport.class);
        if (beanFactoryBeanNamesForTypes != null) {
            for (String beanFactoryBeanName : beanFactoryBeanNamesForTypes) {
                PlaceholderConfigurerSupport placeholderConfigurerSupport = beanFactory.getBean(beanFactoryBeanName, PlaceholderConfigurerSupport.class);
                refreshSinglePlaceholderConfigurerSupport(beanFactory, placeholderConfigurerSupport);
            }
        }
    }

    /**
     * refresh PropertySourcesPlaceholderConfigurer or PropertyPlaceholderConfigurer.
     * The usual way is as following :
     * 1. define PropertyPlaceholderConfigurer/PropertySourcesPlaceholderConfigurer bean
     *
     * @param beanFactory
     * @param placeholderConfigurerSupport
     * @Bean public static PropertySourcesPlaceholderConfigurer properties(){
     * PropertySourcesPlaceholderConfigurer pspc
     * = new PropertySourcesPlaceholderConfigurer();
     * Resource[] resources = new ClassPathResource[ ]
     * { new ClassPathResource( "foo.properties" ) };
     * pspc.setLocations( resources );
     * pspc.setIgnoreUnresolvablePlaceholders( true );
     * return pspc;
     * }
     * 2. define PropertyPlaceholderConfigurer/PropertySourcesPlaceholderConfigurer bean in xml
     * <bean class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">
     * <property name="locations" value="classpath:foo.properties" />
     * </bean>
     */
    private static void refreshSinglePlaceholderConfigurerSupport(DefaultListableBeanFactory beanFactory, PlaceholderConfigurerSupport placeholderConfigurerSupport) {
        if (placeholderConfigurerSupport instanceof PropertySourcesPlaceholderConfigurer) {
            PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = (PropertySourcesPlaceholderConfigurer) placeholderConfigurerSupport;
            // if placeholderConfigurerSupport is PropertySourcesPlaceholderConfigurer instance, it should clear and reload propertySources
            // 1. get orig propertySources
            MutablePropertySources origPropertySources = getPropertySources(propertySourcesPlaceholderConfigurer);
            // 2. clear propertySources, so it can be reinitialized
            origPropertySources.forEach(propertySource -> origPropertySources.remove(propertySource.getName()));
            ReflectionHelper.set(propertySourcesPlaceholderConfigurer, "propertySources", null);
            // 3. reinitialize propertySources. It will generate new propertySources
            propertySourcesPlaceholderConfigurer.postProcessBeanFactory(beanFactory);
            // 4. get new propertySources
            MutablePropertySources curPropertySources = getPropertySources(propertySourcesPlaceholderConfigurer);
            // 5. add new propertySources elements to orig propertySources
            curPropertySources.forEach(propertySource -> origPropertySources.addLast(propertySource));
            // 6 set orig propertySources to placeholderConfigurerSupport.
            // we should keep origPropertySources, because it is used other objects, such as StringValueResolver.
            ReflectionHelper.set(propertySourcesPlaceholderConfigurer, "propertySources", origPropertySources);
        } else if (placeholderConfigurerSupport instanceof PropertyPlaceholderConfigurer) {
            PropertyPlaceholderConfigurer propertyPlaceholderConfigurer = (PropertyPlaceholderConfigurer) placeholderConfigurerSupport;
            propertyPlaceholderConfigurer.postProcessBeanFactory(beanFactory);
        }
    }

    private static MutablePropertySources getPropertySources(PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer) {
        return (MutablePropertySources) ReflectionHelper.getNoException(propertySourcesPlaceholderConfigurer, propertySourcesPlaceholderConfigurer.getClass(), "propertySources");
    }

    /**
     * Deal with the condition:
     * 1. constructor contains @Value parameter
     * 2. @Bean method contains @Value parameter
     *
     * @param beanFactory
     * @return
     */
    public static Set<String> getContainValueAnnotationBeans(DefaultListableBeanFactory beanFactory) {
        Set<String> needRecreateBeans = new HashSet<>();
        // resolve constructor arguments
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition instanceof AnnotatedBeanDefinition) {
                if (beanDefinition instanceof RootBeanDefinition) {
                    RootBeanDefinition currentBeanDefinition = (RootBeanDefinition) beanDefinition;
                    if (containValueAnnotationInMethod(beanFactory, currentBeanDefinition)) {
                        needRecreateBeans.add(beanName);
                    }
                } else if (beanDefinition instanceof GenericBeanDefinition) {
                    GenericBeanDefinition currentBeanDefinition = (GenericBeanDefinition) beanDefinition;
                    AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) currentBeanDefinition;
                    if (AnnotatedBeanDefinitionUtils.getFactoryMethodMetadata(annotatedBeanDefinition) != null) {
                        continue;
                    }
                    if (BeanFactoryProcessor.needReloadOnConstructor(beanFactory, currentBeanDefinition, beanName, constructors -> checkConstructorContainsValueAnnotation(constructors))) {
                        needRecreateBeans.add(beanName);
                    }
                }
            }
        }
        return needRecreateBeans;
    }

    private static boolean containValueAnnotationInMethod(DefaultListableBeanFactory beanFactory, RootBeanDefinition currentBeanDefinition) {
        if (currentBeanDefinition.getFactoryMethodName() != null && currentBeanDefinition.getFactoryBeanName() != null) {
            Method method = currentBeanDefinition.getResolvedFactoryMethod();
            if (method == null) {
                Object factoryBean = beanFactory.getBean(currentBeanDefinition.getFactoryBeanName());
                Class factoryClass = ClassUtils.getUserClass(factoryBean.getClass());
                Method[] methods = getCandidateMethods(factoryClass, currentBeanDefinition);
                for (Method m : methods) {
                    if (!Modifier.isStatic(m.getModifiers()) && currentBeanDefinition.isFactoryMethod(m) &&
                            m.getParameterCount() != 0 && AnnotatedBeanDefinitionUtils.containValueAnnotation(m.getParameterAnnotations())) {
                        return true;
                    }
                }
            } else if (method.getParameterCount() != 0) {
                // @Bean method contains @Value parameter
                if (AnnotatedBeanDefinitionUtils.containValueAnnotation(method.getParameterAnnotations())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
                    (mbd.isNonPublicAccessAllowed() ?
                            ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
        } else {
            return (mbd.isNonPublicAccessAllowed() ?
                    ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
        }
    }

    private static boolean checkConstructorContainsValueAnnotation(Constructor<?>[] constructors) {
        for (Constructor constructor : constructors) {
            if (constructor.getParameterCount() != 0 && AnnotatedBeanDefinitionUtils.containValueAnnotation(constructor.getParameterAnnotations())) {
                return true;
            }
        }
        return false;
    }
}
