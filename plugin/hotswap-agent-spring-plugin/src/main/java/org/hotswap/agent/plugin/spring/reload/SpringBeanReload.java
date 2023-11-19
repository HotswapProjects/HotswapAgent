package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.*;
import org.hotswap.agent.plugin.spring.files.PropertyReload;
import org.hotswap.agent.plugin.spring.files.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
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
    // it is synchronized set because it is used synchronized block
    private Set<URL> xmls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // it is synchronized set because it is used synchronized block
    private Set<BeanDefinitionHolder> newScanBeanDefinitions = new HashSet<>();
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
        try {
            beanFactoryAssistant.setReload(true);
            LOGGER.info("##### start reloading '{}' with timestamp '{}'", ObjectUtils.identityToString(beanFactory), changeTimeStamps);
            LOGGER.trace("SpringReload:{},  beanFactory:{}", this, beanFactory);
            BeanFactoryProcessor.setAllowBeanDefinitionOverriding(beanFactory, true);
            do {
                doReload();
            } while (checkHasChange() && printReloadLog());
            return true;
        } finally {
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
            // 4. add changed class into recreate beans
            refreshChangedClasses();
            // 5. load new beans from scanning. The newBeanNames is used to print the suitable log.
            refreshNewBean();
            // 6. destroy bean including factory bean
            destroyBeans();
            // rerun the while loop if it has changes
            if (checkHasChange() && printReloadLog()) {
                continue;
            }

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
        LOGGER.debug("the class or the file at '{}' has changes, rerun the while loop", ObjectUtils.identityToString(beanFactory));
        return true;
    }

    private boolean checkHasChange() {
        if (properties.isEmpty() && classes.isEmpty() && xmls.isEmpty() && newScanBeanDefinitions.isEmpty()) {
            LOGGER.trace("no change, ignore reloading '{}'", ObjectUtils.identityToString(beanFactory));
            return false;
        }
        return true;
    }

    private boolean refreshProperties() {
        synchronized (properties) {
            if (!properties.isEmpty()) {
                PropertyReload.reloadPropertySource(beanFactory);
                beansToProcess.addAll(PropertyReload.getContainValueAnnotationBeans(beanFactory));
                beansToProcess.addAll(beanFactoryAssistant.placeHolderXmlMapping.keySet());
                // clear properties
                properties.clear();
                return true;
            }
        }
        return false;
    }

    private void reloadXmlBeanDefinitions(boolean propertiesChanged) {
        Set<String> result = XmlBeanDefinitionScannerAgent.reloadXmlsAndGetBean(beanFactory, propertiesChanged,
                beanFactoryAssistant.placeHolderXmlMapping, beansToProcess, xmls);
        processedBeans.addAll(result);
    }

    private void refreshChangedClasses() {
        Set<String> configurationBeansToReload = new HashSet<>();
        synchronized (classes) {
            for (Class clazz : classes) {
                destroyClasses.add(ClassUtils.getUserClass(clazz).getName());
                String[] names = beanFactory.getBeanNamesForType(clazz);
                if (names != null && names.length > 0) {
                    beansToProcess.addAll(Arrays.asList(names));
                    // 3.1 when the bean is @Configuration, it should be recreated.
                    configurationBeansToReload.addAll(reloadAnnotatedBeanDefinitions(clazz, names));
                } else {
                    LOGGER.debug("the bean of class {} not found", clazz.getName());
                }
            }
            // 3.2 when the bean is factory bean, it should be recreated.
            if (!configurationBeansToReload.isEmpty()) {
                for (String beanName : beanFactory.getBeanDefinitionNames()) {
                    BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                    String factoryBeanName = beanDefinition.getFactoryBeanName();
                    if (factoryBeanName != null && configurationBeansToReload.contains(factoryBeanName)) {
                        LOGGER.debug("the bean '{}' will be recreating because the factory bean '{}' is changed", beanName, factoryBeanName);
                        beanFactory.removeBeanDefinition(beanName);
                    }
                }
            }
            // 3.3 clear class cache
            LOGGER.trace("clear class cache of {}", ObjectUtils.identityToString(beanFactory));
            classes.clear();
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
        newBeanNames.addAll(newBeanNames);
    }

    private void preInstantiateSingleton() {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            // recreate the bean and do the initialization
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
                if (beansToProcess.contains(beanName)) {
                    LOGGER.reload("bean '{}' is protoType and reloaded", beanName);
                }
            }
        }
    }

    private void refreshRequestMapping() {
        // reset mvc initialized, it will update the mapping of url and handler
        ResetRequestMappingCaches.reset(beanFactory);
    }

    private void processAutowiredAnnotationBeans() {
        AutowiredAnnotationProcessor.processSingletonBeanInjection(beanFactory);
    }

    private void processConfigBeanDefinitions() {
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).postProcess(beanFactory);
    }

    private void clearSpringCache() {
        // spring won't rebuild dependency map if injectionMetadataCache is not cleared
        // which lead to singletons depend on beans in xml won't be destroy and recreate, may be a spring bug?
        ResetSpringStaticCaches.reset();
        ResetBeanPostProcessorCaches.reset(beanFactory);
        ResetTransactionAttributeCaches.reset(beanFactory);
        ResetBeanFactoryPostProcessorCaches.reset(beanFactory);
        // fixme temperately disable it
//        ResetBeanFactoryCaches.reset(beanFactory);
        ConfigurationClassPostProcessorEnhance.getInstance(beanFactory).postProcess(beanFactory);
        ResetAnnotationCache.resetAnnotationScanner(beanFactory);
    }

    private void clearLocalCache() {
        beansToProcess.clear();
        newBeanNames.clear();
    }

    private List<String> reloadAnnotatedBeanDefinitions(Class clazz, String[] beanNames) {
        List<String> configurationBeansToReload = new ArrayList<>();
        for (String beanName : beanNames) {
            if (beanName.startsWith("&")) {
                beanName = beanName.substring(1);
            }
            BeanDefinition beanDefinition = BeanFactoryProcessor.getBeanDefinition(beanFactory, beanName);
            if (AnnotationHelper.hasAnnotation(clazz, "org.springframework.context.annotation.Configuration")
                    && beanDefinition.getAttribute("org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass") != null) {
                configurationBeansToReload.add(beanName);
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
            LOGGER.debug("the bean '{}' will be recreating because the factory class '{}' is changed", beanName,
                    beanDefinition.getBeanClassName());
            return true;
        } else if (beanDefinition.getFactoryBeanName() != null && processedBeans.contains(beanDefinition.getFactoryBeanName())) {
            LOGGER.debug("the bean '{}' will be recreating because the factory bean '{}' is changed", beanName,
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SpringReload{");
        sb.append("classes=").append(classes);
        sb.append(", properties=").append(properties);
        sb.append(", xmls=").append(xmls);
        sb.append(", dependentBeanMap=").append(dependentBeanMap);
        sb.append(", destroyBeans=").append(processedBeans);
        sb.append(", recreatedBeans=").append(beansToProcess);
        sb.append('}');
        return sb.toString();
    }
}
