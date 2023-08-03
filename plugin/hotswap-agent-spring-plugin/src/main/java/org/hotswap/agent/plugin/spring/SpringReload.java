package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.AutowiredAnnotationProcessor;
import org.hotswap.agent.plugin.spring.core.BeanFactoryProcessor;
import org.hotswap.agent.plugin.spring.core.PropertyReload;
import org.hotswap.agent.plugin.spring.core.XmlReload;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.transformers.PlaceholderConfigurerSupportTransformer;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.hotswap.agent.util.ReflectionHelper.get;

public class SpringReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PlaceholderConfigurerSupportTransformer.class);

    private Set<Class> classes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<URL> properties = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<URL> xmls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final SpringGlobalCaches springGlobalCaches;
    private DefaultListableBeanFactory beanFactory;
    private final Map<String, Set<String>> dependentBeanMap;

    private Set<String> destroyBeans = new HashSet<>();
    private Set<String> newBeans = new HashSet<>();
    private Set<Class> newClass = new HashSet<>();
    private Set<String> recreatedBeans = new HashSet<>();
    private Set<String> needInjectedBeans = new HashSet<>();

    public SpringReload(DefaultListableBeanFactory beanFactory, SpringGlobalCaches springGlobalCaches) {
        this.springGlobalCaches = springGlobalCaches;
        this.beanFactory = beanFactory;
        this.dependentBeanMap = (Map<String, Set<String>>) get(beanFactory, "dependentBeanMap");
    }

    public void addClass(Class clazz) {
        classes.add(clazz);
    }

    public void addProperty(URL property) {
        properties.add(property);
    }

    public void addXml(URL xml) {
        xmls.add(xml);
    }

    public void appendAll(SpringReload content) {
        classes.addAll(content.classes);
        properties.addAll(content.properties);
        xmls.addAll(content.xmls);
    }

    public boolean reload() throws IOException {
        if (!properties.isEmpty()) {
            PropertyReload.reloadPropertySource(beanFactory);
            recreatedBeans.addAll(PropertyReload.checkAndGetReloadBeanNames(beanFactory));
            recreatedBeans.addAll(springGlobalCaches.placeHolderXmlRelation.keySet());
        }
        // clear cache
        // spring won't rebuild dependency map if injectionMetadataCache is not cleared
        // which lead to singletons depend on beans in xml won't be destroy and recreate, may be a spring bug?
        ResetSpringStaticCaches.reset();
        ResetBeanPostProcessorCaches.reset(beanFactory);
        ResetBeanFactoryPostProcessorCaches.reset(beanFactory);
        ProxyReplacer.clearAllProxies();

        ResetBeanFactoryCaches.reset(beanFactory);
        // reload xmls: the beans will be destroyed
        Set<String> reloadDefinitionNames = XmlReload.reloadXmlsAndGetBean(!this.properties.isEmpty(), springGlobalCaches.placeHolderXmlRelation, recreatedBeans, xmls);
        destroyBeans.addAll(reloadDefinitionNames);
        // add changed class into recreate beans
        for (Class clazz : classes) {
            String[] names = beanFactory.getBeanNamesForType(clazz);
            if (names != null && names.length > 0) {
                recreatedBeans.addAll(Arrays.asList(names));
            }
        }
        // destroy bean
        destroyBeans();

        // beanDefinition enhanced: BeanFactoryPostProcessor
        invokeBeanFactoryPostProcessors(beanFactory);
        addBeanPostProcessors(beanFactory);
        // injected beans
        for (String beanName : needInjectedBeans) {
            Object bean = beanFactory.getSingleton(beanName);
            if (bean != null) {
                // will inject properties of object including autowired properties
                beanFactory.configureBean(bean, beanName);
            }
        }
        // process @Value and @Autowired of singleton beans excluding destroyed beans
        AutowiredAnnotationProcessor.processSingletonBeanInjection(beanFactory);

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            // 重新创建bean，并初始化
            beanFactory.getBean(beanName);
        }

        return true;
    }

    private void destroyBeans() {
        for (String beanName : recreatedBeans) {
            destroyBean(beanName);
        }
    }

    private void destroyBean(String beanName) {
        // FactoryBean case
        boolean isFactoryBean = false;

        if (beanName != null && beanName.startsWith("&")) {
            try {
                beanFactory.getBeanDefinition(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                BeanDefinition mergedBeanDefinition = beanFactory.getMergedBeanDefinition(beanName);
                if (mergedBeanDefinition instanceof RootBeanDefinition) {
                    RootBeanDefinition rootBeanDefinition = (RootBeanDefinition) mergedBeanDefinition;
                    isFactoryBean = BeanFactoryProcessor.isFactoryBean(this.beanFactory, beanName, rootBeanDefinition);
                }
                beanName = beanName.substring(1);
            }
        }
        if (destroyBeans.contains(beanName)) {
            return;
        }
        destroyBeans.add(beanName);
        String[] dependentBeans = beanFactory.getDependentBeans(beanName);

        for (String dependentBean : dependentBeans) {
            BeanDefinition beanDefinition;
            try {
                beanDefinition = beanFactory.getBeanDefinition(dependentBean);
            } catch (NoSuchBeanDefinitionException e) {
                //ignore
                LOGGER.debug("bean not found: " + dependentBean + ", it is depended by: " + beanName);
                continue;
            }
            if (!beanDefinition.isSingleton()) {
                continue;
            }
//            if (destroyBeans.contains(dependentBean)) {
//                continue;
//            }
            Object instance = beanFactory.getSingleton(dependentBean);
            if (isFactoryBean) {
                destroyBean(dependentBean);
            } else if (needDestroy(dependentBean, beanDefinition, instance)) {
                destroyBean(dependentBean);
            } else {
                this.needInjectedBeans.add(dependentBean);
            }
        }
        dependentBeanMap.remove(beanName);
        BeanFactoryProcessor.destroySingleton(beanFactory, beanName);
        dependentBeanMap.put(beanName, new HashSet<>(Arrays.asList(dependentBeans)));
    }

    private boolean needDestroy(String beanName, BeanDefinition beanDefinition, Object instance) {
        if (beanDefinition.getConstructorArgumentValues().getArgumentCount() > 0) {
            return true;
        }
        if (instance instanceof FactoryBean) {
            return true;
        }
        // check factory method (xml case)
        if (beanDefinition instanceof AbstractBeanDefinition) {
            AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
            if (abstractBeanDefinition.getFactoryBeanName() != null && abstractBeanDefinition.getFactoryMethodName() != null) {
                if (recreatedBeans.contains(abstractBeanDefinition.getFactoryBeanName()) || destroyBeans.contains(abstractBeanDefinition.getFactoryBeanName())) {
                    return true;
                }
            }
        }
        // check factory method and constructor
        if (beanDefinition instanceof RootBeanDefinition) {
            RootBeanDefinition rootBeanDefinition = (RootBeanDefinition) beanDefinition;
            if (rootBeanDefinition.getResolvedFactoryMethod() != null) {
                Method method = rootBeanDefinition.getResolvedFactoryMethod();
                Class<?>[] parameterizedTypes = method.getParameterTypes();
                for (Class<?> parameterizedType : parameterizedTypes) {
                    if (parameterizedType.isPrimitive()) {
                        continue;
                    }
                    if (parameterizedType == String.class) {
                        continue;
                    }
                    String[] beanNames = beanFactory.getBeanNamesForType(parameterizedType);
                    for (String bn : beanNames) {
                        if (destroyBeans.contains(bn) || recreatedBeans.contains(bn)) {
                            return true;
                        }
                    }
                }
            }
        } else if (beanDefinition instanceof GenericBeanDefinition) {
            GenericBeanDefinition genericBeanDefinition = (GenericBeanDefinition) beanDefinition;
            boolean reload = BeanFactoryProcessor.checkNeedReload(beanFactory, genericBeanDefinition, beanName, constructors -> {
                if (constructors != null || constructors.length == 0) {
                    if (constructors.length == 1) {
                        return containBeanDependencyAtConstruct(constructors[0]);
                    }
                    for (Constructor<?> constructor : constructors) {
                        if (constructor.getParameterCount() != 0 && containBeanDependencyAtConstruct(constructor)) {
                            return true;
                        }
                    }
                }
                return false;
            });
            if (reload) {
                return true;
            }
        }
        // check init method
        if (beanDefinition.isSingleton() && beanDefinition instanceof AbstractBeanDefinition) {
            AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
            if (abstractBeanDefinition.getInitMethodName() != null) {
                recreatedBeans.add(beanName);
            }
        } else if (beanDefinition.isSingleton() && instance instanceof InitializingBean) {
            recreatedBeans.add(beanName);
        }

        return false;
    }

    private boolean containBeanDependencyAtConstruct(Constructor<?> constructor) {
        Class<?>[] classes = constructor.getParameterTypes();
        if (classes == null || classes.length == 0) {
            return false;
        }
        for (Class<?> clazz : classes) {
            if (clazz.isPrimitive()) {
                continue;
            }
            if (clazz == String.class) {
                continue;
            }
            String[] beanNames = beanFactory.getBeanNamesForType(clazz);
            if (beanNames != null && beanNames.length > 0) {
                return true;
            }
        }
        return false;
    }

    private static void invokeBeanFactoryPostProcessors(DefaultListableBeanFactory factory) {
        try {
            invokePostProcessorRegistrationDelegate(factory);
        } catch (Throwable t) {
            LOGGER.debug("Failed to invoke PostProcessorRegistrationDelegate, possibly Spring version is 3.x or less", t);
            invokeBeanFactoryPostProcessors0(factory);
        }
    }

    private static void invokePostProcessorRegistrationDelegate(DefaultListableBeanFactory factory) throws Throwable {
        Class<?> clazz = Class.forName("org.springframework.context.support.PostProcessorRegistrationDelegate",
                true, factory.getClass().getClassLoader());
        Method method = clazz.getDeclaredMethod("invokeBeanFactoryPostProcessors",
                ConfigurableListableBeanFactory.class, List.class);
        method.setAccessible(true);
        method.invoke(null, factory, Collections.emptyList());
    }

    private static void invokeBeanFactoryPostProcessors0(DefaultListableBeanFactory factory) {
        String[] bdrppNames = factory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
        for (String name : bdrppNames) {
            BeanDefinitionRegistryPostProcessor pp = factory.getBean(name, BeanDefinitionRegistryPostProcessor.class);
            pp.postProcessBeanDefinitionRegistry(factory);
        }

        for (String name : bdrppNames) {
            BeanDefinitionRegistryPostProcessor pp = factory.getBean(name, BeanDefinitionRegistryPostProcessor.class);
            pp.postProcessBeanFactory(factory);
        }

        String[] bfppNames = factory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);
        for (String name : bfppNames) {
            if (Arrays.asList(bdrppNames).contains(name)) {
                continue;
            }

            BeanFactoryPostProcessor pp = factory.getBean(name, BeanFactoryPostProcessor.class);
            pp.postProcessBeanFactory(factory);
        }
    }

    private static void addBeanPostProcessors(DefaultListableBeanFactory factory) {
        String[] names = factory.getBeanNamesForType(BeanPostProcessor.class, true, false);
        for (String name : names) {
            BeanPostProcessor pp = factory.getBean(name, BeanPostProcessor.class);
            factory.addBeanPostProcessor(pp);
            LOGGER.debug("Add BeanPostProcessor {}", name);
        }
    }

    public void clear() {
        classes.clear();
        properties.clear();
        xmls.clear();
        newBeans.clear();
        recreatedBeans.clear();
        needInjectedBeans.clear();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SpringReload{");
        sb.append("classes=").append(classes);
        sb.append(", properties=").append(properties);
        sb.append(", xmls=").append(xmls);
        sb.append(", dependentBeanMap=").append(dependentBeanMap);
        sb.append(", destroyBeans=").append(destroyBeans);
        sb.append(", newBeans=").append(newBeans);
        sb.append(", newClass=").append(newClass);
        sb.append(", recreatedBeans=").append(recreatedBeans);
        sb.append(", needInjectedBeans=").append(needInjectedBeans);
        sb.append('}');
        return sb.toString();
    }
}
