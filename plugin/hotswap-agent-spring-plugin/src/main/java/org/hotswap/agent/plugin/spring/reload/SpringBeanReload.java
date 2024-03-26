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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.*;
import org.hotswap.agent.plugin.spring.files.PropertyReload;
import org.hotswap.agent.plugin.spring.files.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.listener.SpringEventSource;
import org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle;
import org.hotswap.agent.plugin.spring.utils.ResourceUtils;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.hotswap.agent.plugin.spring.utils.RegistryUtils.maybeRegistryToBeanFactory;
import static org.hotswap.agent.util.ReflectionHelper.get;

/**
 * Reload spring beans.
 */
public class SpringBeanReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringBeanReload.class);

    private AtomicBoolean isReloading = new AtomicBoolean(false);

    // it is synchronized set because it is used synchronized block
    private Set<Class<?>> classes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // it is synchronized set because it is used synchronized block
    private Set<URL> properties = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<URL> yamls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // it is synchronized set because it is used synchronized block
    private Set<URL> xmls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // it is synchronized set because it is used synchronized block
    private Set<BeanDefinitionHolder> newScanBeanDefinitions = new HashSet<>();
    private Set<String> changedBeanNames = new HashSet<>();
    Set<String> newBeanNames = new HashSet<>();

    DefaultListableBeanFactory beanFactory;
    private final Map<String, Set<String>> dependentBeanMap;

    private Set<String> processedBeans = new HashSet<>();
    private Set<String> destroyClasses = new HashSet<>();
    private Set<String> beansToProcess = new HashSet<>();
    private final BeanNameGenerator beanNameGenerator;
    private final BeanFactoryAssistant beanFactoryAssistant;


    public SpringBeanReload(DefaultListableBeanFactory beanFactory) {
        this.beanFactoryAssistant = new BeanFactoryAssistant(beanFactory);
        beanNameGenerator = new AnnotationBeanNameGenerator();
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
        synchronized (classes) {
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
    }

    public void addProperty(URL property) {
        if (property == null) {
            return;
        }
        synchronized (properties) {
            if (properties.add(property)) {
                LOGGER.info("try to add changed property '{}' into {}", property, ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.debug("try to add changed property '{}' into {}", property, ObjectUtils.identityToString(beanFactory));
            }
        }
    }

    public void addYaml(URL property) {
        if (property == null) {
            return;
        }
        synchronized (yamls) {
            if (yamls.add(property)) {
                LOGGER.info("try to add changed yaml '{}' into {}", property, ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.debug("try to add changed yaml '{}' into {}, but exist", property, ObjectUtils.identityToString(beanFactory));
            }
        }
    }

    public void addScanNewBean(BeanDefinitionRegistry registry, BeanDefinitionHolder beanDefinitionHolder) {
        if (beanDefinitionHolder == null) {
            return;
        }
        DefaultListableBeanFactory defaultListableBeanFactory = maybeRegistryToBeanFactory(registry);
        if (defaultListableBeanFactory != null) {
            if (defaultListableBeanFactory.equals(beanFactory)) {
                synchronized (newScanBeanDefinitions) {
                    newScanBeanDefinitions.add(beanDefinitionHolder);
                    LOGGER.info("add new spring bean '{}' into {}", beanDefinitionHolder.getBeanName(), ObjectUtils.identityToString(beanFactory));
                    return;
                }
            }
        }
        LOGGER.debug("'{}' is not '{}' or the newBean is exist, ignore it", registry, defaultListableBeanFactory);
    }

    public void addXml(URL xml) {
        if (xml == null) {
            return;
        }
        synchronized (xmls) {
            if (xmls.add(xml)) {
                LOGGER.info("try to add xml '{}' into {}", xml, ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.debug("try to add xml '{}' into {}", xml, ObjectUtils.identityToString(beanFactory));
            }
        }
    }

    public void addChangedBeanNames(String[] beanNames) {
        if (beanNames == null) {
            return;
        }
        synchronized (this.changedBeanNames) {
            if (this.changedBeanNames.addAll(Arrays.asList(beanNames))) {
                LOGGER.debug("try to add changed beanNames '{}' into {}", Arrays.asList(beanNames), ObjectUtils.identityToString(beanFactory));
            } else {
                LOGGER.trace("try to add changed beanNames '{}' into {}, but exist", Arrays.asList(beanNames), ObjectUtils.identityToString(beanFactory));
            }
        }
    }

    public void collectPlaceHolderProperties() {
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            doCollectPlaceHolderProperties(beanName, beanDefinition);
        }
    }

    private void doCollectPlaceHolderProperties(String beanName, BeanDefinition beanDefinition) {
        if (beanDefinition.getPropertyValues() != null) {
            for (PropertyValue pv : beanDefinition.getPropertyValues().getPropertyValues()) {
                String resourcePath = getPlaceHolderBeanResource(pv.getValue(), beanName, beanDefinition);
                if (resourcePath != null) {
                    beanFactoryAssistant.placeHolderXmlMapping.put(beanName, resourcePath);
                    return;
                }
            }
        }
        if (beanDefinition.getConstructorArgumentValues().isEmpty()) {
            return;
        }
        for (ConstructorArgumentValues.ValueHolder valueHolder : beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
            String resourcePath = getPlaceHolderBeanResource(valueHolder.getValue(), beanName, beanDefinition);
            if (resourcePath != null) {
                beanFactoryAssistant.placeHolderXmlMapping.put(beanName, resourcePath);
                return;
            }
        }
        for (ConstructorArgumentValues.ValueHolder valueHolder : beanDefinition.getConstructorArgumentValues().getGenericArgumentValues()) {
            String resourcePath = getPlaceHolderBeanResource(valueHolder.getValue(), beanName, beanDefinition);
            if (resourcePath != null) {
                beanFactoryAssistant.placeHolderXmlMapping.put(beanName, resourcePath);
                return;
            }
        }
    }

    private String getPlaceHolderBeanResource(Object object, String beanName, BeanDefinition beanDefinition) {
        if (!isPlaceHolderBean(object)) {
            return null;
        }
        if (beanDefinition instanceof AbstractBeanDefinition) {
            return ResourceUtils.getPath(((AbstractBeanDefinition) beanDefinition).getResource());
        }
        return null;
    }

    private boolean isPlaceHolderBean(Object v) {
        String value = null;
        if (v instanceof TypedStringValue) {
            value = ((TypedStringValue) v).getValue();
        } else if (v instanceof String) {
            value = (String) v;
        }
        if (value == null) {
            return false;
        }
        if (value.startsWith(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX) &&
                value.endsWith(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX)) {
            return true;
        }
        return false;
    }

    public boolean reload(long changeTimeStamps) {
        if (!preCheckReload()) {
            return false;
        }
        boolean allowBeanDefinitionOverriding = BeanFactoryProcessor.isAllowBeanDefinitionOverriding(beanFactory);
        long now = System.currentTimeMillis();
        ClassLoader origContextClassLoader = null;
        try {
            origContextClassLoader = ClassUtils.overrideThreadContextClassLoader(beanFactory.getBeanClassLoader());
            beanFactoryAssistant.setReload(true);
            LOGGER.info("##### start reloading '{}' with timestamp '{}'", ObjectUtils.identityToString(beanFactory), changeTimeStamps);
            LOGGER.trace("SpringReload:{},  beanFactory:{}", this, beanFactory);
            BeanFactoryProcessor.setAllowBeanDefinitionOverriding(beanFactory, true);
            do {
                doReload();
            } while (checkHasChange() && printReloadLog());
            return true;
        } finally {
            ClassUtils.overrideThreadContextClassLoader(origContextClassLoader);
            beanFactoryAssistant.increaseReloadTimes();
            BeanFactoryProcessor.setAllowBeanDefinitionOverriding(beanFactory, allowBeanDefinitionOverriding);
            LOGGER.info("##### [{}th] finish reloading '{}', it cost {}ms", beanFactoryAssistant.getReloadTimes(),
                    ObjectUtils.identityToString(beanFactory), System.currentTimeMillis() - now);
        }
    }

    private void doReload() {
        // when there are changes, it will rerun the while loop
        while (true) {
            // 1. clear cache
            clearSpringCache();
            // 2. properties reload
            boolean propertiesChanged = refreshProperties();
            // 3. reload xmls: the beans will be destroyed
            reloadXmlBeanDefinitions(propertiesChanged);
            // 4. add changed classes and changed beans into recreate beans
            refreshChangedClassesAndBeans();

            // 5. load new beans from scanning. The newBeanNames is used to print the suitable log.
            refreshNewBean();
            // 6. destroy bean including factory bean
            destroyBeans();
            // rerun the while loop if it has changes
            if (checkHasChange() && printReloadLog()) {
                continue;
            }
            //beanDefinition enhanced: BeanFactoryPostProcessor
            ProxyReplacer.clearAllProxies();

            // 7. invoke the Bean lifecycle steps
            // 7.1 invoke BeanFactoryPostProcessor
            invokeBeanFactoryPostProcessors(beanFactory);
            addBeanPostProcessors(beanFactory);
            // 7.2 process @Value and @Autowired of singleton beans excluding destroyed beans
            processAutowiredAnnotationBeans();
            // 7.3 process @Configuration
            processConfigBeanDefinitions();
            // 8. skip the while loop if no change
            if (!checkHasChange()) {
                break;
            }
        }

        // 9 invoke getBean to instantiate singleton beans
        preInstantiateSingleton();
        // 10 reset mvc initialized, it will update the mapping of url and handler
        refreshRequestMapping();
        // 11 clear all process cache
        clearLocalCache();
    }

    private boolean preCheckReload() {
        if (!checkHasChange()) {
            return false;
        }
        // check the classes is bean of spring, if not remove it
        synchronized (classes) {
            if (!classes.isEmpty()) {
                Iterator<Class<?>> iterator = classes.iterator();
                while (iterator.hasNext()) {
                    Class<?> clazz = iterator.next();
                    String[] names = beanFactory.getBeanNamesForType(clazz);
                    // if the class is not spring bean or Factory Class, remove it
                    if ((names == null || names.length == 0) && !isFactoryMethod(clazz)) {
                        LOGGER.trace("the class '{}' is not spring bean or factory class", clazz.getName());
                        iterator.remove();
                    } else {
                        LOGGER.debug("the class '{}' is spring bean or factory class", clazz.getName());
                    }
                }
            }
        }
        return checkHasChange();
    }

    private boolean isFactoryMethod(Class<?> clazz) {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition.getFactoryMethodName() != null && beanDefinition.getBeanClassName() != null &&
                    clazz.getName().equals(beanDefinition.getBeanClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean printReloadLog() {
        LOGGER.debug("the class or the file at '{}' has changes, rerun the while loop.{}", ObjectUtils.identityToString(beanFactory), this);
        return true;
    }

    private boolean checkHasChange() {
        if (properties.isEmpty() && classes.isEmpty() && xmls.isEmpty() && newScanBeanDefinitions.isEmpty()
                && yamls.isEmpty() && changedBeanNames.isEmpty()) {
            LOGGER.trace("no change, ignore reloading '{}'", ObjectUtils.identityToString(beanFactory));
            return false;
        }
        LOGGER.trace("has change, start reloading '{}', {}", ObjectUtils.identityToString(beanFactory), this);
        return true;
    }

    private boolean refreshProperties() {
        boolean propertiesChanged = false;
        synchronized (properties) {
            if (!properties.isEmpty()) {
                beansToProcess.addAll(beanFactoryAssistant.placeHolderXmlMapping.keySet());
                // clear properties
                properties.clear();
                propertiesChanged = true;
            }
        }
        synchronized (yamls) {
            if (!yamls.isEmpty()) {
                // clear
                yamls.clear();
                propertiesChanged = true;
            }
        }
        if (propertiesChanged) {
            LOGGER.reload("the properties of '{}' is changed", ObjectUtils.identityToString(beanFactory));
            PropertyReload.reloadPropertySource(beanFactory);
            beansToProcess.addAll(PropertyReload.getContainValueAnnotationBeans(beanFactory));
            return true;
        }
        return false;
    }

    private void reloadXmlBeanDefinitions(boolean propertiesChanged) {
        Set<String> result = XmlBeanDefinitionScannerAgent.reloadXmlsAndGetBean(beanFactory, propertiesChanged,
                beanFactoryAssistant.placeHolderXmlMapping, beansToProcess, xmls);
        processedBeans.addAll(result);
    }

    private void refreshChangedClassesAndBeans() {
        LOGGER.debug("refresh changed classes and beans of {}, classes:{}, changedBeans:{}",
                ObjectUtils.identityToString(beanFactory), classes, changedBeanNames);
        Set<String> configurationBeansToReload = new HashSet<>();
        // we should refresh changed classes before refresh changed beans.
        // after refresh class, maybe we will add some changed beans into changedBeanNames
        // 1. refresh changed classes
        refreshChangedClass(configurationBeansToReload::addAll);
        // 2. refresh changed beans
        refreshChangedBeans(configurationBeansToReload::addAll);
        // 3 when the bean is factory bean, it should be recreated.
        resetConfigurationBeanDefinition(configurationBeansToReload);
        LOGGER.trace("clear class cache of {}", ObjectUtils.identityToString(beanFactory));
    }

    private void refreshChangedClass(Consumer<List<String>> reloadBeans) {
        Set<Class> classToProcess;
        synchronized (classes) {
            classToProcess = new HashSet<>(classes);
            classes.clear();
        }
        for (Class clazz : classToProcess) {
            destroyClasses.add(ClassUtils.getUserClass(clazz).getName());
            String[] names = beanFactory.getBeanNamesForType(clazz);
            if (names != null && names.length > 0) {
                LOGGER.trace("the bean of class {} has the bean names {}", clazz.getName(), Arrays.asList(names));
                beansToProcess.addAll(Arrays.asList(names));
                // 3.1 when the bean is @Configuration, it should be recreated.
                reloadBeans.accept(reloadAnnotatedBeanDefinitions(clazz, names));
                // notify the class is changed
                SpringEventSource.INSTANCE.fireEvent(new ClassChangeEvent(clazz, beanFactory));
            } else {
                LOGGER.debug("the bean of class {} not found", clazz.getName());
            }
        }
    }

    private void refreshChangedBeans(Consumer<List<String>> reloadBeans) {
        Set<String> beanNamesToProcess;
        synchronized (changedBeanNames) {
            beanNamesToProcess = new HashSet<>(changedBeanNames);
            changedBeanNames.clear();
        }
        for (String beanName : beanNamesToProcess) {
            beansToProcess.add(beanName);
            // maybe this bean is not exist, or it belongs to other beanFactory
            if (!beanFactory.containsBeanDefinition(beanName)) {
                continue;
            }
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (beanDefinition instanceof AbstractBeanDefinition) {
                AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
                if (abstractBeanDefinition.hasBeanClass()) {
                    Class<?> clazz = abstractBeanDefinition.getBeanClass();
                    reloadBeans.accept(reloadAnnotatedBeanDefinitions(clazz, new String[]{beanName}));
                }
            }
        }
    }

    private void resetConfigurationBeanDefinition(Set<String> configurationBeansToReload) {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            String factoryBeanName = beanDefinition.getFactoryBeanName();
            if (factoryBeanName != null && configurationBeansToReload.contains(factoryBeanName)) {
                LOGGER.debug("the bean '{}' will be recreating because the factory bean '{}' is changed", beanName, factoryBeanName);
                beanFactory.removeBeanDefinition(beanName);
            }
        }
    }

    private void refreshNewBean() {
        Set<String> newBeanNames = new HashSet<>();
        synchronized (newScanBeanDefinitions) {
            for (BeanDefinitionHolder beanDefinitionHolder : newScanBeanDefinitions) {
                BeanDefinitionReaderUtils.registerBeanDefinition(beanDefinitionHolder, beanFactory);
                newBeanNames.add(beanDefinitionHolder.getBeanName());
                LOGGER.debug("Register new bean from scanning: {}", beanDefinitionHolder.getBeanName());
            }
            newScanBeanDefinitions.clear();
        }
        this.newBeanNames.addAll(newBeanNames);
    }

    private void preInstantiateSingleton() {
        LOGGER.debug("preInstantiateSingleton of {}", ObjectUtils.identityToString(beanFactory));
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = null;
            try {
                beanDefinition = beanFactory.getBeanDefinition(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                LOGGER.debug("bean not found: " + beanName);
                continue;
            }
            if (beanDefinition.isSingleton()) {
                try {
                    beanFactory.getBean(beanName);
                } catch (Exception e) {
                    LOGGER.error("Failed to get bean: " + beanName, e);
                }
            }
        }
    }

    private void refreshRequestMapping() {
        // reset mvc initialized, it will update the mapping of url and handler
        LOGGER.debug("refreshRequestMapping of {}", ObjectUtils.identityToString(beanFactory));
        ResetRequestMappingCaches.reset(beanFactory, beansToProcess, newBeanNames);
    }

    private void processAutowiredAnnotationBeans() {
        LOGGER.debug("process @Value and @Autowired of singleton beans of {}", ObjectUtils.identityToString(beanFactory));
        AutowiredAnnotationProcessor.processSingletonBeanInjection(beanFactory);
    }

    private void processConfigBeanDefinitions() {
        LOGGER.debug("process @Configuration of {}", ObjectUtils.identityToString(beanFactory));
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).postProcess(beanFactory);
    }

    private void clearSpringCache() {
        // spring won't rebuild dependency map if injectionMetadataCache is not cleared
        // which lead to singletons depend on beans in xml won't be destroy and recreate, may be a spring bug?
        ResetSpringStaticCaches.reset();
        ResetBeanPostProcessorCaches.reset(beanFactory);
        ResetTransactionAttributeCaches.reset(beanFactory);
        ResetBeanFactoryPostProcessorCaches.reset(beanFactory);
        ProxyReplacer.clearAllProxies();
        // fixme temperately disable it
//        ResetBeanFactoryCaches.reset(beanFactory);
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).resetConfigurationClassPostProcessor(beanFactory);
        ResetAnnotationCache.resetAnnotationScanner(beanFactory);
    }

    private void clearLocalCache() {
        beansToProcess.clear();
        newBeanNames.clear();
        if (beanFactory instanceof BeanFactoryLifecycle) {
            ((BeanFactoryLifecycle) beanFactory).hotswapAgent$clearDestroyBean();
        }
    }

    private List<String> reloadAnnotatedBeanDefinitions(Class clazz, String[] beanNames) {
        List<String> configurationBeansToReload = new ArrayList<>();
        Class realClass = ClassUtils.getUserClass(clazz);
        for (String beanName : beanNames) {
            if (beanName.startsWith("&")) {
                beanName = beanName.substring(1);
            }
            BeanDefinition beanDefinition = BeanFactoryProcessor.getBeanDefinition(beanFactory, beanName);
            if (AnnotationHelper.hasAnnotation(realClass, "org.springframework.context.annotation.Configuration")
                    && beanDefinition.getAttribute("org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass") != null) {
                configurationBeansToReload.add(beanName);
                String generateBeanName = beanNameGenerator.generateBeanName(beanDefinition, beanFactory);
                // the beanName is not the same as generateBeanName, it should register a new bean definition and remove the old one.
                if (!beanName.equals(generateBeanName)) {
                    beanFactory.removeBeanDefinition(beanName);
                    BeanDefinition newBeanDefinition = new AnnotatedGenericBeanDefinition(realClass);
                    beanFactory.registerBeanDefinition(generateBeanName, newBeanDefinition);
                } else {
                    // remove the attribute, and it will recreate the bean
                    ((AbstractBeanDefinition) beanDefinition).setBeanClass(realClass);
                    beanDefinition.removeAttribute("org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass");
                }
            }
        }
        return configurationBeansToReload;
    }

    private void destroyBeans() {
        for (String beanName : new ArrayList<>(beansToProcess)) {
            destroyBean(beanName);
        }
        // destroy factory bean
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (isFactoryMethodAndNeedReload(beanName, beanDefinition)) {
                destroyBean(beanName);
            }
        }
        // clear destroy cache
        processedBeans.clear();
        destroyClasses.clear();
    }

    private boolean isFactoryMethodAndNeedReload(String beanName, BeanDefinition beanDefinition) {
        if (beanDefinition.getFactoryMethodName() == null) {
            return false;
        }
        if (beanDefinition.getBeanClassName() != null && destroyClasses.contains(beanDefinition.getBeanClassName())) {
            LOGGER.debug("the bean '{}' of factory class '{}' is changed", beanName,
                    beanDefinition.getBeanClassName());
            return true;
        } else if (beanDefinition.getFactoryBeanName() != null && processedBeans.contains(beanDefinition.getFactoryBeanName())) {
            LOGGER.debug("the bean '{}' of factory bean '{}' is changed", beanName,
                    beanDefinition.getFactoryBeanName());
            return true;
        }
        return false;
    }

    private void destroyBean(String beanName) {
        // FactoryBean case
        if (beanName != null && beanName.startsWith("&") && !beanFactory.containsBeanDefinition(beanName)) {
            beanName = beanName.substring(1);
        }
        if (processedBeans.contains(beanName)) {
            return;
        }
        String[] dependentBeans = beanFactory.getDependentBeans(beanName);
        LOGGER.debug("the bean '{}' is destroyed, and it is depended by {}", beanName, Arrays.toString(dependentBeans));
        doDestroyBean(beanName);
    }

    private void doDestroyBean(String beanName) {
//        dependentBeanMap.remove(beanName);
        processedBeans.add(beanName);
        Object singletonObject = beanFactory.getSingleton(beanName);
        if (singletonObject != null) {
            destroyClasses.add(ClassUtils.getUserClass(singletonObject).getName());
        }
        BeanFactoryProcessor.destroySingleton(beanFactory, beanName);
//        dependentBeanMap.put(beanName, new HashSet<>(Arrays.asList(dependentBeans)));
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
            LOGGER.debug("try to invoke PostProcessorRegistrationDelegate");
            invokePostProcessorRegistrationDelegate(factory);
        } catch (ClassNotFoundException t) {
            LOGGER.debug("Failed to invoke PostProcessorRegistrationDelegate, possibly Spring version is 3.x or less, {}", t.getMessage());
            invokeBeanFactoryPostProcessors0(factory);
        } catch (NoSuchMethodException t) {
            LOGGER.debug("Failed to invoke PostProcessorRegistrationDelegate, possibly Spring version is 3.x or less, {}", t.getMessage());
            invokeBeanFactoryPostProcessors0(factory);
        } catch (Exception e) {
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
            try {
                pp.postProcessBeanDefinitionRegistry(factory);
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke BeanDefinitionRegistryPostProcessor: {}, reason:{}",
                        pp.getClass().getName(), e.getMessage());
            }
            pp.postProcessBeanDefinitionRegistry(factory);
        }

        for (String name : bdrppNames) {
            BeanDefinitionRegistryPostProcessor pp = factory.getBean(name, BeanDefinitionRegistryPostProcessor.class);
            try {
                pp.postProcessBeanFactory(factory);
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke BeanDefinitionRegistryPostProcessor: {}, reason:{}",
                        pp.getClass().getName(), e.getMessage());
                LOGGER.trace("Failed to invoke BeanDefinitionRegistryPostProcessor", e);
            }
        }

        String[] bfppNames = factory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);
        for (String name : bfppNames) {
            if (Arrays.asList(bdrppNames).contains(name)) {
                continue;
            }
            BeanFactoryPostProcessor pp = factory.getBean(name, BeanFactoryPostProcessor.class);
            try {
                pp.postProcessBeanFactory(factory);
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke BeanDefinitionRegistryPostProcessor: {}, reason:{}",
                        pp.getClass().getName(), e.getMessage());
                LOGGER.trace("Failed to invoke BeanDefinitionRegistryPostProcessor", e);
            }
        }
    }

    private static void addBeanPostProcessors(DefaultListableBeanFactory factory) {
        String[] names = factory.getBeanNamesForType(BeanPostProcessor.class, true, false);
        LOGGER.debug("try to add BeanPostProcessor: {}", Arrays.asList(names));
        for (String name : names) {
            BeanPostProcessor pp = factory.getBean(name, BeanPostProcessor.class);
            factory.addBeanPostProcessor(pp);
            LOGGER.trace("Add BeanPostProcessor '{}' that mapping to {}", name, ObjectUtils.identityToString(pp));
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SpringBeanReload{");
        sb.append("classes=").append(classes);
        sb.append(", properties=").append(properties);
        sb.append(", yamls=").append(yamls);
        sb.append(", xmls=").append(xmls);
        sb.append(", newScanBeanDefinitions=").append(newScanBeanDefinitions);
        sb.append(", changedBeanNames=").append(changedBeanNames);
        sb.append('}');
        return sb.toString();
    }
}
