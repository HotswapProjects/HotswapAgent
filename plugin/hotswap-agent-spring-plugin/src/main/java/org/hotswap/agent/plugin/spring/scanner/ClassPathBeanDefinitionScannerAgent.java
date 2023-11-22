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
package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.plugin.spring.utils.RegistryUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Registers
 *
 * @author Jiri Bubnik
 */
public class ClassPathBeanDefinitionScannerAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanDefinitionScannerAgent.class);

    private static Map<ClassPathBeanDefinitionScanner, ClassPathBeanDefinitionScannerAgent> instances = new HashMap<>();

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    // target scanner this agent shadows
    ClassPathBeanDefinitionScanner scanner;

    // list of basePackages registered with target scanner
    Set<String> basePackages = new HashSet<>();

    // registry obtained from the scanner
    BeanDefinitionRegistry registry;

    // metadata resolver obtained from the scanner
    ScopeMetadataResolver scopeMetadataResolver;

    // bean name generator obtained from the scanner
    BeanNameGenerator beanNameGenerator;

    private Set<BeanDefinition> beanDefinitions = new HashSet<>();

    /**
     * Return an agent instance for a scanner. If the instance does not exists yet, it is created.
     *
     * @param scanner the scanner
     * @return agent instance
     */
    public static ClassPathBeanDefinitionScannerAgent getInstance(ClassPathBeanDefinitionScanner scanner) {
        ClassPathBeanDefinitionScannerAgent classPathBeanDefinitionScannerAgent = instances.get(scanner);
        // registry may be different if there is multiple app. (this is just a temporary solution)
        if (classPathBeanDefinitionScannerAgent == null || classPathBeanDefinitionScannerAgent.registry != scanner.getRegistry()) {
            instances.put(scanner, new ClassPathBeanDefinitionScannerAgent(scanner));
        }
        return instances.get(scanner);
    }

    /**
     * Find scanner agent by base package.
     *
     * @param basePackage the scanner agent or null if no such agent exists
     * @return the agent
     */
    public static ClassPathBeanDefinitionScannerAgent getInstance(String basePackage) {
        for (ClassPathBeanDefinitionScannerAgent scannerAgent : instances.values()) {
            if (scannerAgent.basePackages.contains(basePackage))
                return scannerAgent;
        }
        return null;
    }

    // Create new instance from getInstance(ClassPathBeanDefinitionScanner scanner) and obtain services from the scanner
    private ClassPathBeanDefinitionScannerAgent(ClassPathBeanDefinitionScanner scanner) {
        this.scanner = scanner;

        this.registry = scanner.getRegistry();
        this.scopeMetadataResolver = (ScopeMetadataResolver) ReflectionHelper.get(scanner, "scopeMetadataResolver");
        this.beanNameGenerator = (BeanNameGenerator) ReflectionHelper.get(scanner, "beanNameGenerator");
    }

    /**
     * Initialize base package from ClassPathBeanDefinitionScanner.scan() (hooked by a Transformer)
     *
     * @param basePackage package that Spring will scan
     */
    public void registerBasePackage(String basePackage) {
        this.basePackages.add(basePackage);

        PluginManagerInvoker.callPluginMethod(SpringPlugin.class, getClass().getClassLoader(),
                "registerComponentScanBasePackage", new Class[]{String.class}, new Object[]{basePackage});
    }

    /**
     * Called by a reflection command from SpringPlugin transformer.
     *
     * @param appClassLoader  the class loader - container or application class loader.
     * @param basePackage     base package on witch the transformer was registered, used to obtain associated scanner.
     * @param classDefinition new class definition
     * @throws IOException error working with classDefinition
     */
    public static boolean refreshClassAndCheckReload(ClassLoader appClassLoader, String basePackage, String clazzName, byte[] classDefinition) throws IOException {
        ClassPathBeanDefinitionScannerAgent scannerAgent = getInstance(basePackage);
        if (scannerAgent == null) {
            LOGGER.error("basePackage '{}' not associated with any scannerAgent", basePackage);
            return false;
        }
        return scannerAgent.createBeanDefinitionAndCheckReload(appClassLoader, clazzName, classDefinition);
    }

    /**
     * create beanDefinition if it is new bean. If it is need refresh, return true;
     *
     * @param appClassLoader
     * @param clazzName
     * @param classDefinition
     * @return
     * @throws IOException
     */
    boolean createBeanDefinitionAndCheckReload(ClassLoader appClassLoader, String clazzName, byte[] classDefinition) throws IOException {
        DefaultListableBeanFactory defaultListableBeanFactory = RegistryUtils.maybeRegistryToBeanFactory(registry);
        if (doProcessWhenBeanExist(defaultListableBeanFactory, appClassLoader, clazzName, classDefinition)) {
            LOGGER.debug("the class '{}' is exist at '{}', it will not create new BeanDefinition", clazzName,
                    ObjectUtils.identityToString(defaultListableBeanFactory));
            return true;
        }
        BeanDefinition beanDefinition = resolveBeanDefinition(appClassLoader, classDefinition);
        if (beanDefinition == null) {
            return false;
        }
        String beanName = this.beanNameGenerator.generateBeanName(beanDefinition, registry);
        // check if bean is already registered
        if (registry.containsBeanDefinition(beanName)) {
            LOGGER.debug("Bean definition '{}' already exists", beanName);
            return false;
        }

        beanDefinitions.add(beanDefinition);
        BeanDefinitionHolder beanDefinitionHolder = defineBean(beanDefinition);
        if (beanDefinitionHolder != null) {
            LOGGER.debug("Registering Spring bean '{}'", beanName);
            if (defaultListableBeanFactory != null) {
                SpringChangedAgent.addNewBean(beanDefinitionHolder, defaultListableBeanFactory);
                return true;
            }
        }

        return false;
    }

    private boolean doProcessWhenBeanExist(DefaultListableBeanFactory defaultListableBeanFactory, ClassLoader
            appClassLoader,
                                           String clazzName, byte[] classDefinition) {
        try {
            Class<?> clazz = loadClass(appClassLoader, clazzName, classDefinition);
            if (defaultListableBeanFactory != null && clazz != null) {
                String[] beanNames = defaultListableBeanFactory.getBeanNamesForType(clazz);
                if (beanNames != null && beanNames.length != 0) {
                    SpringChangedAgent.addChangedClass(clazz, defaultListableBeanFactory);
                    return true;
                }
            }
        } catch (Exception t) {
            LOGGER.debug("make class failed", t);
        }
        return false;
    }

    private Class<?> loadClass(ClassLoader appClassLoader,
                               String clazzName, byte[] classDefinition) {
        Class<?> clazz = doLoadClass(appClassLoader, clazzName);
        if (clazz != null) {
            return clazz;
        }
        ClassPool pool = ClassPool.getDefault();
        try {
            CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classDefinition));
            clazz = doLoadClass(appClassLoader, ctClass.getName());
            if (clazz != null) {
                return clazz;
            }
            return ctClass.toClass(appClassLoader, registry.getClass().getProtectionDomain());
        } catch (IOException e) {
            LOGGER.trace("make new class failed, {}", e.getMessage());
            return null;
        } catch (CannotCompileException e) {
            LOGGER.trace("make new class failed, {}", e.getMessage());
            return null;
        }

    }

    private Class<?> doLoadClass(ClassLoader appClassLoader,
                                 String clazzName) {
        try {
            if (clazzName == null || clazzName.isEmpty()) {
                return null;
            }
            String realClassName = clazzName.replaceAll("/", ".");
            return appClassLoader.loadClass(realClassName);
        } catch (ClassNotFoundException e) {
            // ignore
        } catch (NoClassDefFoundError e) {
            // ignore
        }
        return null;
    }

    /**
     * Resolve candidate to a bean definition and (re)load in Spring.
     * Synchronize to avoid parallel bean definition - usually on reload the beans are interrelated
     * and parallel load will cause concurrent modification exception.
     *
     * @param candidate the candidate to reload
     */
    public BeanDefinitionHolder defineBean(BeanDefinition candidate) {
        synchronized (getClass()) { // TODO sychronize on DefaultListableFactory.beanDefinitionMap?

            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
            candidate.setScope(scopeMetadata.getScopeName());
            String beanName = this.beanNameGenerator.generateBeanName(candidate, registry);
            if (checkCandidate(beanName, candidate)) {
                BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                definitionHolder = applyScopedProxyMode(scopeMetadata, definitionHolder, registry);
                LOGGER.debug("Bean definition '{}'", beanName, candidate);
                return definitionHolder;
            }
            return null;
        }


    }

    /**
     * Resolve bean definition from class definition if applicable.
     *
     * @param appClassLoader the class loader - container or application class loader.
     * @param bytes          class definition.
     * @return the definition or null if not a spring bean
     * @throws IOException
     */
    private BeanDefinition resolveBeanDefinition(ClassLoader appClassLoader, byte[] bytes) throws IOException {
        Resource resource = new ByteArrayResource(bytes);
        resetCachingMetadataReaderFactoryCache();
        MetadataReader metadataReader = getMetadataReader(appClassLoader, resource);

        if (isCandidateComponent(metadataReader)) {
            ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
            sbd.setResource(resource);
            sbd.setSource(resource);
            if (isCandidateComponent(sbd)) {
                LOGGER.debug("Identified candidate component class '{}'", metadataReader.getClassMetadata().getClassName());
                return sbd;
            } else {
                LOGGER.debug("Ignored because not a concrete top-level class '{}'", metadataReader.getClassMetadata().getClassName());
                return null;
            }
        } else {
            LOGGER.debug("Ignored because not matching any filter '{}' ", metadataReader.getClassMetadata().getClassName());
            return null;
        }
    }

    private MetadataReader getMetadataReader(ClassLoader appClassLoader, Resource resource) throws IOException {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(appClassLoader);
            return getMetadataReaderFactory().getMetadataReader(resource);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    private MetadataReaderFactory getMetadataReaderFactory() {
        return (MetadataReaderFactory) ReflectionHelper.get(scanner, "metadataReaderFactory");
    }

    // metadataReader contains cache of loaded classes, reset this cache before BeanDefinition is resolved
    private void resetCachingMetadataReaderFactoryCache() {
        if (getMetadataReaderFactory() instanceof CachingMetadataReaderFactory) {
            Map metadataReaderCache = (Map) ReflectionHelper.getNoException(getMetadataReaderFactory(),
                    CachingMetadataReaderFactory.class, "metadataReaderCache");

            if (metadataReaderCache == null)
                metadataReaderCache = (Map) ReflectionHelper.getNoException(getMetadataReaderFactory(),
                        CachingMetadataReaderFactory.class, "classReaderCache");

            if (metadataReaderCache != null) {
                metadataReaderCache.clear();
                LOGGER.trace("Cache cleared: CachingMetadataReaderFactory.clearCache()");
            } else {
                LOGGER.warning("Cache NOT cleared: neither CachingMetadataReaderFactory.metadataReaderCache nor clearCache does not exist.");
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////
    // Access private / protected members
    ////////////////////////////////////////////////////////////////////////////////////////////

    private BeanDefinitionHolder applyScopedProxyMode(
            ScopeMetadata metadata, BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
        return (BeanDefinitionHolder) ReflectionHelper.invoke(null, AnnotationConfigUtils.class,
                "applyScopedProxyMode", new Class[]{ScopeMetadata.class, BeanDefinitionHolder.class, BeanDefinitionRegistry.class},
                metadata, definition, registry);

    }

    private void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
        ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "registerBeanDefinition", new Class[]{BeanDefinitionHolder.class, BeanDefinitionRegistry.class}, definitionHolder, registry);
    }

    private boolean checkCandidate(String beanName, BeanDefinition candidate) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "checkCandidate", new Class[]{String.class, BeanDefinition.class}, beanName, candidate);
    }

    private boolean isCandidateComponent(AnnotatedBeanDefinition sbd) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathScanningCandidateComponentProvider.class,
                "isCandidateComponent", new Class[]{AnnotatedBeanDefinition.class}, sbd);
    }

    private boolean isCandidateComponent(MetadataReader metadataReader) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathScanningCandidateComponentProvider.class,
                "isCandidateComponent", new Class[]{MetadataReader.class}, metadataReader);
    }
}
