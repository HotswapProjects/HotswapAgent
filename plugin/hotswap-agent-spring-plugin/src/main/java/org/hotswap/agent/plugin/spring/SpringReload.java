package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.*;
import org.hotswap.agent.plugin.spring.files.PropertyReload;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.utils.AnnotatedBeanDefinitionUtils;
import org.hotswap.agent.plugin.spring.files.XmlReload;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hotswap.agent.plugin.spring.utils.RegistryUtils.maybeRegistryToBeanFactory;
import static org.hotswap.agent.util.ReflectionHelper.get;

/**
 * Reload spring beans.
 */
public class SpringReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringReload.class);

    private AtomicBoolean isReloading = new AtomicBoolean(false);

    private Set<Class> classes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<URL> properties = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<URL> xmls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    DefaultListableBeanFactory beanFactory;
    private final Map<String, Set<String>> dependentBeanMap;

    private Set<String> destroyBeans = new HashSet<>();
    private Set<String> destroyClasses = new HashSet<>();
    //    private Set<String> newBeans = new HashSet<>();
    private Set<BeanDefinitionHolder> newScanBeanDefinitions = new HashSet<>();
    private Set<Class> newClass = new HashSet<>();
    private Set<String> recreatedBeans = new HashSet<>();
    private Set<String> needInjectedBeans = new HashSet<>();
    private BeanNameGenerator beanNameGenerator;
    private BeanFactoryAssistant beanFactoryAssistant;


    public SpringReload(DefaultListableBeanFactory beanFactory, BeanFactoryAssistant beanFactoryAssistant) {
        this.beanFactoryAssistant = beanFactoryAssistant;
        this.beanFactory = beanFactory;
        this.dependentBeanMap = (Map<String, Set<String>>) get(beanFactory, "dependentBeanMap");
    }

    public void addClass(Class clazz) {
        if (clazz == null) {
            return;
        }
        String simpleName = clazz.getSimpleName();
        String userClassSimpleName = ClassUtils.getUserClass(clazz).getSimpleName();
        boolean sameClass = simpleName.equals(userClassSimpleName);

        if (classes.add(clazz)) {
            if (sameClass) {
                LOGGER.info("try to add changed class '{}' into {}", clazz.getName(), ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.info("try to add changed class '{}({})' into {}", clazz.getName(), userClassSimpleName, ObjectUtils.identityToString(beanFactory));
            }
        } else {
            if (sameClass) {
                LOGGER.debug("try to add changed class '{}' into {}, but it is exist", clazz.getName(), ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.debug("try to add changed class '{}({})' into {}, but it is exist", clazz.getName(), userClassSimpleName, ObjectUtils.identityToString(beanFactory));
            }
        }
    }

    public void addProperty(URL property) {
        if (property == null) {
            return;
        }
        if (properties.add(property)) {
            LOGGER.info("try to add changed property '{}' into {}", property, ObjectUtils.identityToString(beanFactory));
        } else {
            LOGGER.debug("try to add changed property '{}' into {}", property, ObjectUtils.identityToString(beanFactory));
        }
    }

    public void addScanNewBean(BeanDefinitionRegistry registry, BeanDefinitionHolder beanDefinitionHolder) {
        if (beanDefinitionHolder == null) {
            return;
        }
        DefaultListableBeanFactory defaultListableBeanFactory = maybeRegistryToBeanFactory(registry);
        if (defaultListableBeanFactory != null) {
            if (defaultListableBeanFactory.equals(beanFactory) && newScanBeanDefinitions.add(beanDefinitionHolder)) {
                LOGGER.info("add new spring bean '{}' into {}", beanDefinitionHolder.getBeanName(), ObjectUtils.identityToString(beanFactory));
                return;
            }
        }
        LOGGER.debug("'{}' is not '{}' or the newBean is exist, ignore it", registry, defaultListableBeanFactory);
    }

    public void addXml(URL xml) {
        if (xml == null) {
            return;
        }
        if (xmls.add(xml)) {
            LOGGER.info("try to add xml '{}' into {}", xml, ObjectUtils.identityToString(beanFactory));
        } else {
            LOGGER.debug("try to add xml '{}' into {}", xml, ObjectUtils.identityToString(beanFactory));
        }
    }

    public void appendAll(SpringReload content) {
        classes.addAll(content.classes);
        properties.addAll(content.properties);
        xmls.addAll(content.xmls);
        newScanBeanDefinitions.addAll(content.newScanBeanDefinitions);
    }

    public boolean reload() throws IOException {
        boolean allowBeanDefinitionOverriding = BeanFactoryProcessor.isAllowBeanDefinitionOverriding(beanFactory);
        long now = System.currentTimeMillis();
        try {
            beanFactoryAssistant.setReload(true);
            LOGGER.info("****************************************************************************************************");
            LOGGER.info("##### start reloading '{}'", ObjectUtils.identityToString(beanFactory));
            LOGGER.trace("SpringReload:{},  beanFactory:{}", this, beanFactory);
            BeanFactoryProcessor.setAllowBeanDefinitionOverriding(beanFactory, true);
            return doReload();
        } finally {
            beanFactoryAssistant.increaseReloadTimes();
            BeanFactoryProcessor.setAllowBeanDefinitionOverriding(beanFactory, allowBeanDefinitionOverriding);
            LOGGER.info("##### [{}th] finish reloading '{}', it cost {}ms", beanFactoryAssistant.getReloadTimes(),
                    ObjectUtils.identityToString(beanFactory), System.currentTimeMillis() - now);
            LOGGER.info("****************************************************************************************************");
        }
    }

    private boolean doReload() {
        // 0. properties reload
        if (!properties.isEmpty()) {
            PropertyReload.reloadPropertySource(beanFactory);
            recreatedBeans.addAll(PropertyReload.checkAndGetReloadBeanNames(beanFactory));
            recreatedBeans.addAll(beanFactoryAssistant.placeHolderXmlRelation.keySet());
        }
        // 1. clear cache
        clearCache();

        // 2. reload xmls: the beans will be destroyed
        Set<String> reloadDefinitionNames = XmlReload.reloadXmlsAndGetBean(beanFactory, !this.properties.isEmpty(),
                beanFactoryAssistant.placeHolderXmlRelation, recreatedBeans, xmls);
        destroyBeans.addAll(reloadDefinitionNames);
        // 3. add changed class into recreate beans
        List<String> needReloadBeanDefinitionNames = new ArrayList<>();
        for (Class clazz : classes) {
            destroyClasses.add(ClassUtils.getUserClass(clazz).getName());
            String[] names = beanFactory.getBeanNamesForType(clazz);
            if (names != null && names.length > 0) {
                recreatedBeans.addAll(Arrays.asList(names));
                // 3.1 when the bean is @Configuration, it should be recreated.
                reloadAnnotatedBeanDefinitions(clazz, names, needReloadBeanDefinitionNames);
            } else {
                LOGGER.debug("the bean of class {} not found", clazz.getName());
            }
        }
        // 3.2 when the bean is factory bean, it should be recreated.
        if (!needReloadBeanDefinitionNames.isEmpty()) {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                String factoryBeanName = beanDefinition.getFactoryBeanName();
                if (factoryBeanName != null && needReloadBeanDefinitionNames.contains(factoryBeanName)) {
                    LOGGER.debug("the bean '{}' will be recreating because the factory bean '{}' is changed", beanName, factoryBeanName);
                    beanFactory.removeBeanDefinition(beanName);
                }
            }
        }
        // 4. load new beans from scanning
        Set<String> newBeanNames = new HashSet<>();
        for (BeanDefinitionHolder beanDefinitionHolder : newScanBeanDefinitions) {
            BeanDefinitionReaderUtils.registerBeanDefinition(beanDefinitionHolder, beanFactory);
            newBeanNames.add(beanDefinitionHolder.getBeanName());
            LOGGER.debug("Register new bean from scanning: {}", beanDefinitionHolder.getBeanName());
        }
        // 5.destroy bean including factory bean
        destroyBeans();
        //beanDefinition enhanced: BeanFactoryPostProcessor
        ProxyReplacer.clearAllProxies();

        // 6. invoke the Bean lifecycle steps
        // 6.1 invoke BeanFactoryPostProcessor
        invokeBeanFactoryPostProcessors(beanFactory);
        addBeanPostProcessors(beanFactory);
        // 6.2 invoke configureBean to populated properties with new beans
        for (String beanName : needInjectedBeans) {
            Object bean = beanFactory.getSingleton(beanName);
            if (bean != null) {
                // will inject properties of object including autowired properties
                LOGGER.reload("the bean '{}' is reconfigured because the dependency bean is changed. Properties:{}", beanName,
                        Arrays.asList(beanFactory.getDependenciesForBean(beanName)));
                beanFactory.configureBean(bean, beanName);
            }
        }
        // 6.3 process @Value and @Autowired of singleton beans excluding destroyed beans
        AutowiredAnnotationProcessor.processSingletonBeanInjection(beanFactory);
        // 6.4 reset again
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).postProcess(beanFactory);
        // 6.5 invoke getBean to instantiate singleton beans
        preInstantiateSingleton(newBeanNames);
        // 6.6 reset mvc initialized, it will update the mapping of url and handler
        ResetRequestMappingCaches.reset(beanFactory);
        return true;
    }

    private void preInstantiateSingleton(Set<String> newBeanNames) {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            // 重新创建bean，并初始化
            Object singleton = beanFactory.getSingleton(beanName);
            BeanDefinition beanDefinition = null;
            try {
                beanDefinition = beanFactory.getBeanDefinition(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                LOGGER.debug("bean not found: " + beanName);
                continue;
            }
            if (beanDefinition.isSingleton()) {
                Object target = beanFactory.getBean(beanName);
                if (singleton == null) {
                    if (newBeanNames.contains(beanName)) {
                        LOGGER.reload("the bean '{}' of {} is singleton and new created", beanName, ClassUtils.getUserClass(target).getName());
                    } else {
                        LOGGER.reload("the bean '{}' of {} is singleton and recreated", beanName, ClassUtils.getUserClass(target).getName());
                    }
                }
            } else if (beanDefinition.isPrototype()) {
                if (recreatedBeans.contains(beanName)) {
                    LOGGER.reload("bean '{}' is protoType and reloaded", beanName);
                }
            }
        }
    }

    private void clearCache() {
        // spring won't rebuild dependency map if injectionMetadataCache is not cleared
        // which lead to singletons depend on beans in xml won't be destroy and recreate, may be a spring bug?
        ResetSpringStaticCaches.reset();
        ResetBeanPostProcessorCaches.reset(beanFactory);
        ResetTransactionAttributeCaches.reset(beanFactory);
        ResetBeanFactoryPostProcessorCaches.reset(beanFactory);
        ProxyReplacer.clearAllProxies();
        // fixme temperately disable it
//        ResetBeanFactoryCaches.reset(beanFactory);
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).postProcess(beanFactory);
        ResetAnnotationCache.resetAnnotationScanner(beanFactory);
    }

    private void reloadAnnotatedBeanDefinitions(Class clazz, String[] beanNames, List<String> needReloadBeanDefinitionNames) {
        for (String beanName : beanNames) {
            if (beanName.startsWith("&")) {
                beanName = beanName.substring(1);
            }
            BeanDefinition beanDefinition = BeanFactoryProcessor.getBeanDefinition(beanFactory, beanName);
            if (AnnotationHelper.hasAnnotation(clazz, "org.springframework.context.annotation.Configuration")
                    && beanDefinition.getAttribute("org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass") != null) {
                needReloadBeanDefinitionNames.add(beanName);
                String generateBeanName = beanNameGenerator.generateBeanName(beanDefinition, beanFactory);
                // the beanName is not the same as generateBeanName, it should register a new bean definition and remove the old one.
                if (!beanName.equals(generateBeanName)) {
                    beanFactory.removeBeanDefinition(beanName);
                    BeanDefinition newBeanDefinition = new AnnotatedGenericBeanDefinition(clazz);
                    beanFactory.registerBeanDefinition(generateBeanName, newBeanDefinition);
                } else {
                    // remove the attribute, and it will recreate the bean
                    ((AbstractBeanDefinition) beanDefinition).setBeanClass(clazz);
                    beanDefinition.removeAttribute("org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass");
                }
            }
        }
    }

    private void destroyBeans() {
        for (String beanName : new ArrayList<>(recreatedBeans)) {
            destroyBean(beanName);
        }
        // destroy factory bean
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.getFactoryMethodName() != null && beanDefinition.getBeanClassName() != null &&
                    destroyClasses.contains(beanDefinition.getBeanClassName())) {
                LOGGER.debug("the bean '{}' will be recreating because the factory class '{}' is changed", beanName, beanDefinition.getBeanClassName());
                destroyBean(beanName);
            } else if (beanDefinition.getFactoryMethodName() != null && beanDefinition.getFactoryBeanName() != null &&
                    destroyBeans.contains(beanDefinition.getFactoryBeanName())) {
                LOGGER.debug("the bean '{}' will be recreating because the factory bean '{}' is changed", beanName, beanDefinition.getFactoryBeanName());
                destroyBean(beanName);
            }
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
        String[] dependentBeans = beanFactory.getDependentBeans(beanName);
        LOGGER.debug("the bean '{}' is destroyed, and it is depended by {}", beanName, Arrays.toString(dependentBeans));
        doDestroySingleBean(beanName, dependentBeans);

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
    }

    private void doDestroySingleBean(String beanName, String[] dependentBeans) {
        dependentBeanMap.remove(beanName);
        destroyBeans.add(beanName);
        Object singletonObject = beanFactory.getSingleton(beanName);
        if (singletonObject != null) {
            destroyClasses.add(ClassUtils.getUserClass(singletonObject).getName());
        }
        BeanFactoryProcessor.destroySingleton(beanFactory, beanName);
        dependentBeanMap.put(beanName, new HashSet<>(Arrays.asList(dependentBeans)));
    }

    private boolean needDestroy(String beanName, BeanDefinition beanDefinition, Object instance) {
        if (instance == null) {
            LOGGER.debug("the bean '{}' is not created", beanName);
            return true;
        }
        if (beanDefinition.getConstructorArgumentValues().getArgumentCount() > 0) {
            return true;
        }
        if (instance instanceof FactoryBean) {
            return true;
        }
        // if the bean is a proxy, it should be destroyed.
        String clazzName = instance.getClass().getName();
        if (clazzName.startsWith("com.sun.proxy.$Proxy") || clazzName.contains("$$EnhancerBySpringCGLIB") ||
                clazzName.contains("$$EnhancerByCGLIB")) {
            return true;
        }

        // check factory method (xml case)
        if (!(beanDefinition instanceof AbstractBeanDefinition)) {
            return false;
        }
        AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
        if (abstractBeanDefinition.getFactoryBeanName() != null && abstractBeanDefinition.getFactoryMethodName() != null) {
            if (recreatedBeans.contains(abstractBeanDefinition.getFactoryBeanName()) || destroyBeans.contains(abstractBeanDefinition.getFactoryBeanName())) {
                return true;
            }
        }
        // check factory method and constructor
        Method method = null;
        if (beanDefinition instanceof RootBeanDefinition) {
            RootBeanDefinition rootBeanDefinition = (RootBeanDefinition) beanDefinition;
            if (rootBeanDefinition.getResolvedFactoryMethod() != null) {
                method = rootBeanDefinition.getResolvedFactoryMethod();
            }
        }
        if (method == null && beanDefinition instanceof AnnotatedBeanDefinition) {
            AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
            /**
             * @since 4.1.1
             */
            MethodMetadata methodMetadata = AnnotatedBeanDefinitionUtils.getFactoryMethodMetadata(annotatedBeanDefinition);
            if (methodMetadata != null && methodMetadata instanceof StandardMethodMetadata) {
                StandardMethodMetadata standardMethodMetadata = (StandardMethodMetadata) methodMetadata;
                method = standardMethodMetadata.getIntrospectedMethod();
            }
        }
        if (method != null) {
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

        boolean reload = BeanFactoryProcessor.checkNeedReload(beanFactory, abstractBeanDefinition, beanName, constructors -> {
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
        // check init method
        if (beanDefinition.isSingleton() && beanDefinition instanceof AbstractBeanDefinition) {
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
        } catch (ClassNotFoundException t) {
            LOGGER.debug("Failed to invoke PostProcessorRegistrationDelegate, possibly Spring version is 3.x or less, {}", t.getMessage());
            invokeBeanFactoryPostProcessors0(factory);
        } catch (NoSuchMethodException t) {
            LOGGER.debug("Failed to invoke PostProcessorRegistrationDelegate, possibly Spring version is 3.x or less, {}", t.getMessage());
            invokeBeanFactoryPostProcessors0(factory);
        } catch (InvocationTargetException e) {
            LOGGER.error("Failed to invoke PostProcessorRegistrationDelegate", e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to invoke PostProcessorRegistrationDelegate", e);
            throw new RuntimeException(e);
        }
    }

    private static void invokePostProcessorRegistrationDelegate(DefaultListableBeanFactory factory) throws NoSuchMethodException,
            ClassNotFoundException, InvocationTargetException, IllegalAccessException {
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
            LOGGER.debug("Add BeanPostProcessor {} that mapping to {}", name, ObjectUtils.identityToString(pp));
        }
    }

    public void clear() {
        classes.clear();
        properties.clear();
        xmls.clear();
//        newBeans.clear();
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
//        sb.append(", newBeans=").append(newBeans);
        sb.append(", newClass=").append(newClass);
        sb.append(", recreatedBeans=").append(recreatedBeans);
        sb.append(", needInjectedBeans=").append(needInjectedBeans);
        sb.append('}');
        return sb.toString();
    }

    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }
}
