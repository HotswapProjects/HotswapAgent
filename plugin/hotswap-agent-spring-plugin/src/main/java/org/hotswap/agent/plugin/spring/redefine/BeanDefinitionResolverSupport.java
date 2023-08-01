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
package org.hotswap.agent.plugin.spring.redefine;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.ResetBeanPostProcessorCaches;
import org.hotswap.agent.plugin.spring.ResetRequestMappingCaches;
import org.hotswap.agent.plugin.spring.ResetSpringStaticCaches;
import org.hotswap.agent.plugin.spring.ResetTransactionAttributeCaches;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.plugin.spring.processor.ConfigurationClassPostProcessorAgent;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.context.annotation.ScopeMetadata;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;

import static org.hotswap.agent.plugin.spring.utils.RegistryUtils.maybeRegistryToBeanFactory;

public abstract class BeanDefinitionResolverSupport implements BeanDefinitionResolver {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(BeanDefinitionResolverSupport.class);
    private static final Object LOCK = new Object();

    @Override
    public void redefine(ClassLoader appClassLoader, byte[] classDefinition) {
        ScannedGenericBeanDefinition candidate = resolveBeanDefinition(appClassLoader, classDefinition);
        if (candidate != null) {
            defineBean(candidate);
        }
    }

    /**
     * Resolve bean definition from class definition if applicable.
     *
     * @param appClassLoader the class loader - container or application class loader.
     * @param bytes          class definition.
     * @return the definition or null if not a spring bean
     */
    private ScannedGenericBeanDefinition resolveBeanDefinition(ClassLoader appClassLoader, byte[] bytes) {
        try {
            MetadataReaderFactory metadataReaderFactory = getMetadataReaderFactory();
            Resource resource = new ByteArrayResource(bytes);
            MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
            ScannedGenericBeanDefinition bd = new ScannedGenericBeanDefinition(metadataReader);
            bd.setResource(resource);
            bd.setSource(resource);
            return (isCandidateComponent(metadataReader, bd) ? bd : null);
        } catch (IOException e) {
            LOGGER.trace("Unable to resolve bean definition because exception occurs when read bean's metadata", e);
            return null;
        }
    }

    /**
     * Resolve candidate to a bean definition and (re)load in Spring.
     * Synchronize to avoid parallel bean definition - usually on reload the beans are interrelated
     * and parallel load will cause concurrent modification exception.
     *
     * @param candidate the candidate to reload
     */
    private void defineBean(ScannedGenericBeanDefinition candidate) {
        synchronized (LOCK) {
            BeanDefinitionRegistry registry = getBeanDefinitionRegistry();

            // set scope
            ScopeMetadata scopeMetadata = getScopeMetadataResolver().resolveScopeMetadata(candidate);
            candidate.setScope(scopeMetadata.getScopeName());

            // process bean definition
            String beanName = getBeanNameGenerator().generateBeanName(candidate, registry);
            processBeanDefinition(beanName, candidate);

            // reset cache
            resetBeanFactory();
            resetStaticCaches();

            // remove bean
            removeBean(beanName);

            if (checkCandidate(beanName, candidate)) {
                BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                definitionHolder = applyScopedProxyMode(scopeMetadata, definitionHolder, registry);

                LOGGER.reload("Registering Spring bean '{}'", beanName);
                LOGGER.debug("Bean definition '{}'", beanName, candidate);

                // register new bean definition
                registerBeanDefinition(definitionHolder, registry);

                // continue to post-process bean definition registry if the changed class is an instance of
                // configuration class.
                boolean isConfigurationClass = isConfigurationClassCandidate(candidate);
                if (isConfigurationClass) {
                    LOGGER.debug("Bean definition '{}' is a configuration class", beanName);
                    removeBeansFromFactoryBean(beanName);
                    ConfigurationClassPostProcessorAgent.getInstance().postProcess(registry, beanName);
                }

                ProxyReplacer.clearAllProxies();
                freezeConfiguration();
            }
        }
    }

    /**
     * Get the bean definition registry.
     *
     * @return the bean definition registry
     */
    protected abstract BeanDefinitionRegistry getBeanDefinitionRegistry();

    /**
     * Get the bean name generator.
     *
     * @return the bean name generator
     */
    protected abstract BeanNameGenerator getBeanNameGenerator();

    /**
     * Registry bean definition to registry.
     *
     * @param definitionHolder the bean definition holder
     * @param registry         the bean definition registry
     */
    protected abstract void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry);

    /**
     * Get the scope metadata resolver.
     *
     * @return the scope metadata resolver
     */
    protected abstract ScopeMetadataResolver getScopeMetadataResolver();

    /**
     * Get the metadata reader factory.
     *
     * @return the metadata reader factory
     */
    protected abstract MetadataReaderFactory getMetadataReaderFactory();

    /**
     * Post process bean definition before register it to the bean definition registry.
     *
     * @param candidate the candidate bean definition
     * @param beanName  the bean name
     */
    protected abstract void postProcessBeanDefinition(ScannedGenericBeanDefinition candidate, String beanName);

    /**
     * Check if the candidate bean definition is a valid spring component
     *
     * @param metadataReader the metadata reader
     * @param beanDefinition the bean definition
     * @return true if the candidate is a valid spring component
     */
    protected abstract boolean isCandidateComponent(MetadataReader metadataReader, AnnotatedBeanDefinition beanDefinition);


    /**
     * Check the given candidate's bean name, determining whether the corresponding bean definition needs to be
     * registered or conflicts with an existing definition.
     *
     * @param beanName  the suggested name for the bean
     * @param candidate the candidate bean definition
     * @return true if the bean can be registered as-is
     */
    protected abstract boolean checkCandidate(String beanName, BeanDefinition candidate);

    private void resetBeanFactory() {
        DefaultListableBeanFactory bf = maybeRegistryToBeanFactory(getBeanDefinitionRegistry());
        if (bf != null) {
            ResetRequestMappingCaches.reset(bf);
            ResetBeanPostProcessorCaches.reset(bf);
            ResetTransactionAttributeCaches.reset(bf);
        }
    }

    private void resetStaticCaches() {
        ResetSpringStaticCaches.reset();
    }

    private void processBeanDefinition(String beanName, ScannedGenericBeanDefinition candidate) {
        postProcessBeanDefinition(candidate, beanName);
        processCommonDefinitionAnnotations(candidate);
    }

    private static BeanDefinitionHolder applyScopedProxyMode(ScopeMetadata metadata, BeanDefinitionHolder definition,
                                                             BeanDefinitionRegistry registry) {
        return (BeanDefinitionHolder) ReflectionHelper.invoke(null, AnnotationConfigUtils.class, "applyScopedProxyMode",
                new Class[]{ScopeMetadata.class, BeanDefinitionHolder.class, BeanDefinitionRegistry.class},
                metadata, definition, registry);

    }

    private static void processCommonDefinitionAnnotations(ScannedGenericBeanDefinition candidate) {
        ReflectionHelper.invoke(null, AnnotationConfigUtils.class, "processCommonDefinitionAnnotations",
                new Class[]{AnnotatedBeanDefinition.class}, candidate);
    }

    private boolean isConfigurationClassCandidate(BeanDefinition beanDef) {
        MetadataReaderFactory metadataReaderFactory = getMetadataReaderFactory();
        Object o = ReflectionHelper.invokeNoException(null,
                "org.springframework.context.annotation.ConfigurationClassUtils",
                metadataReaderFactory.getClass().getClassLoader(),
                "checkConfigurationClassCandidate",
                new Class[]{BeanDefinition.class, MetadataReaderFactory.class},
                beanDef, metadataReaderFactory);
        return o != null && (Boolean) o;
    }

    /**
     * If registry contains the bean, remove it first (destroying existing singletons).
     *
     * @param beanName name of the bean
     */
    private void removeBean(String beanName) {
        BeanDefinitionRegistry registry = getBeanDefinitionRegistry();
        if (registry.containsBeanDefinition(beanName)) {
            LOGGER.debug("Removing bean definition '{}'", beanName);
            registry.removeBeanDefinition(beanName);
        }
    }

    private void removeBeansFromFactoryBean(String factoryBean) {
        BeanDefinitionRegistry registry = getBeanDefinitionRegistry();
        for (String name : registry.getBeanDefinitionNames()) {
            if (factoryBean.equals(registry.getBeanDefinition(name).getFactoryBeanName())) {
                registry.removeBeanDefinition(name);
                LOGGER.debug("Removing bean definition '{}' since it's created from factory bean '{}'", name, factoryBean);
            }
        }
    }

    /**
     * Freeze all bean definitions
     */
    private void freezeConfiguration() {
        BeanDefinitionRegistry registry = getBeanDefinitionRegistry();
        if (registry instanceof DefaultListableBeanFactory) {
            ((DefaultListableBeanFactory) registry).freezeConfiguration();
        } else if (registry instanceof GenericApplicationContext) {
            (((GenericApplicationContext) registry).getDefaultListableBeanFactory()).freezeConfiguration();
        }
    }
}
