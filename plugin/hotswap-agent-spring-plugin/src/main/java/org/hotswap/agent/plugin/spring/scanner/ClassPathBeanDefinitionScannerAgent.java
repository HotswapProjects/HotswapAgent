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

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.ResetSpringStaticCaches;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.plugin.spring.redefine.BeanDefinitionResolverSupport;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Registers
 *
 * @author Jiri Bubnik
 */
public class ClassPathBeanDefinitionScannerAgent extends BeanDefinitionResolverSupport {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanDefinitionScannerAgent.class);

    private static final Map<ClassPathBeanDefinitionScanner, ClassPathBeanDefinitionScannerAgent> instances = new HashMap<>();

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    // target scanner this agent shadows
    private final ClassPathBeanDefinitionScanner scanner;

    // list of basePackages registered with target scanner
    private final Set<String> basePackages = new HashSet<>();

    // registry obtained from the scanner
    private final BeanDefinitionRegistry registry;

    // metadata resolver obtained from the scanner
    private final ScopeMetadataResolver scopeMetadataResolver;

    // bean name generator obtained from the scanner
    private final BeanNameGenerator beanNameGenerator;

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
     */
    public static void refreshClass(ClassLoader appClassLoader, String basePackage, byte[] classDefinition) {
        ResetSpringStaticCaches.reset();

        ClassPathBeanDefinitionScannerAgent scannerAgent = getInstance(basePackage);
        if (scannerAgent == null) {
            LOGGER.error("basePackage '{}' not associated with any scannerAgent", basePackage);
            return;
        }

        scannerAgent.redefine(appClassLoader, classDefinition);

        reloadFlag = false;
    }

    @Override
    protected MetadataReaderFactory getMetadataReaderFactory() {
        MetadataReaderFactory factory = (MetadataReaderFactory) ReflectionHelper.get(scanner, "metadataReaderFactory");

        // metadataReader contains cache of loaded classes, reset this cache before BeanDefinition is resolved
        if (factory instanceof CachingMetadataReaderFactory) {
            Map metadataReaderCache = (Map) ReflectionHelper.getNoException(factory,
                    CachingMetadataReaderFactory.class,
                    "metadataReaderCache");

            if (metadataReaderCache == null)
                metadataReaderCache = (Map) ReflectionHelper.getNoException(factory,
                        CachingMetadataReaderFactory.class,
                        "classReaderCache");

            if (metadataReaderCache != null) {
                metadataReaderCache.clear();
                LOGGER.debug("Cache cleared: CachingMetadataReaderFactory.clearCache()");
            } else {
                LOGGER.warning("Cache NOT cleared: neither CachingMetadataReaderFactory.metadataReaderCache nor " +
                        "clearCache does not exist.");
            }
        }

        return factory;
    }


    @Override
    protected BeanDefinitionRegistry getBeanDefinitionRegistry() {
        return this.registry;
    }

    @Override
    protected BeanNameGenerator getBeanNameGenerator() {
        return this.beanNameGenerator;
    }

    @Override
    protected ScopeMetadataResolver getScopeMetadataResolver() {
        return this.scopeMetadataResolver;
    }

    @Override
    protected boolean checkCandidate(String beanName, BeanDefinition candidate) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "checkCandidate", new Class[]{String.class, BeanDefinition.class}, beanName, candidate);
    }

    @Override
    protected boolean isCandidateComponent(MetadataReader metadataReader, AnnotatedBeanDefinition beanDefinition) {
        String className = metadataReader.getClassMetadata().getClassName();
        if (isCandidateComponent(metadataReader)) {
            if (isCandidateComponent(beanDefinition)) {
                LOGGER.debug("Identified candidate component class '{}'", className);
                return true;
            } else {
                LOGGER.debug("Ignored because not a concrete top-level class '{}'", className);
                return false;
            }
        } else {
            LOGGER.trace("Ignored because not matching any filter '{}' ", className);
            return false;
        }
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
        ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class, "registerBeanDefinition",
                new Class[]{BeanDefinitionHolder.class, BeanDefinitionRegistry.class},
                definitionHolder, registry);
    }

    @Override
    protected void postProcessBeanDefinition(ScannedGenericBeanDefinition candidate, String beanName) {
        ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "postProcessBeanDefinition", new Class[]{AbstractBeanDefinition.class, String.class},
                candidate, beanName);
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
