///*
// * Copyright 2013-2023 the HotswapAgent authors.
// *
// * This file is part of HotswapAgent.
// *
// * HotswapAgent is free software: you can redistribute it and/or modify it
// * under the terms of the GNU General Public License as published by the
// * Free Software Foundation, either version 2 of the License, or (at your
// * option) any later version.
// *
// * HotswapAgent is distributed in the hope that it will be useful, but
// * WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// * Public License for more details.
// *
// * You should have received a copy of the GNU General Public License along
// * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
// */
//package org.hotswap.agent.plugin.spring.reader;
//
//import org.hotswap.agent.javassist.ClassPool;
//import org.hotswap.agent.javassist.CtClass;
//import org.hotswap.agent.logging.AgentLogger;
//import org.hotswap.agent.plugin.spring.core.ResetSpringStaticCaches;
//import org.hotswap.agent.plugin.spring.redefine.BeanDefinitionResolverSupport;
//import org.hotswap.agent.plugin.spring.transformers.AnnotatedBeanDefinitionReaderTransformer;
//import org.hotswap.agent.util.ReflectionHelper;
//import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
//import org.springframework.beans.factory.config.BeanDefinition;
//import org.springframework.beans.factory.config.BeanDefinitionHolder;
//import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.beans.factory.support.BeanNameGenerator;
//import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
//import org.springframework.context.annotation.ScannedGenericBeanDefinition;
//import org.springframework.context.annotation.ScopeMetadataResolver;
//import org.springframework.core.type.AnnotatedTypeMetadata;
//import org.springframework.core.type.classreading.MetadataReader;
//import org.springframework.core.type.classreading.MetadataReaderFactory;
//import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
//
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//
//public class AnnotatedBeanDefinitionReaderManager extends BeanDefinitionResolverSupport {
//    private static final AgentLogger LOGGER = AgentLogger.getLogger(AnnotatedBeanDefinitionReaderManager.class);
//
//    private static final Map<AnnotatedBeanDefinitionReader, AnnotatedBeanDefinitionReaderManager> instances = new HashMap<>();
//
//    public static boolean reloadFlag = false;
//
//    private final AnnotatedBeanDefinitionReader reader;
//    private final BeanDefinitionRegistry registry;
//    private final ScopeMetadataResolver scopeMetadataResolver;
//    private final BeanNameGenerator beanNameGenerator;
//    private final SimpleMetadataReaderFactory metadataReaderFactory;
//
//    private final Set<String> componentClasses = new HashSet<>();
//
//    private AnnotatedBeanDefinitionReaderManager(AnnotatedBeanDefinitionReader reader) {
//        this.reader = reader;
//
//        this.registry = reader.getRegistry();
//        this.scopeMetadataResolver = (ScopeMetadataResolver) ReflectionHelper.get(reader, "scopeMetadataResolver");
//        this.beanNameGenerator = (BeanNameGenerator) ReflectionHelper.get(reader, "beanNameGenerator");
//        this.metadataReaderFactory = new SimpleMetadataReaderFactory(reader.getClass().getClassLoader());
//    }
//
//
//    @Override
//    protected void postProcessBeanDefinition(ScannedGenericBeanDefinition candidate, String beanName) {
//        // no-op
//    }
//
//    @Override
//    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
//        ReflectionHelper.invoke(null, BeanDefinitionReaderUtils.class,
//                "registerBeanDefinition", new Class[]{BeanDefinitionHolder.class, BeanDefinitionRegistry.class},
//                definitionHolder, registry);
//    }
//
//    @Override
//    protected BeanDefinitionRegistry getBeanDefinitionRegistry() {
//        return this.registry;
//    }
//
//    @Override
//    protected BeanNameGenerator getBeanNameGenerator() {
//        return this.beanNameGenerator;
//    }
//
//    @Override
//    protected ScopeMetadataResolver getScopeMetadataResolver() {
//        return this.scopeMetadataResolver;
//    }
//
//    @Override
//    protected MetadataReaderFactory getMetadataReaderFactory() {
//        return this.metadataReaderFactory;
//    }
//
//    @Override
//    protected boolean isCandidateComponent(MetadataReader metadataReader, AnnotatedBeanDefinition beanDefinition) {
//        Object o = ReflectionHelper.getNoException(reader, AnnotatedBeanDefinitionReader.class, "conditionEvaluator");
//        if (o != null) {
//            Boolean b = (Boolean) ReflectionHelper.invokeNoException(o,
//                    "org.springframework.context.annotation.ConditionEvaluator",
//                    reader.getClass().getClassLoader(),
//                    "shouldSkip",
//                    new Class[]{AnnotatedTypeMetadata.class},
//                    beanDefinition.getMetadata());
//            return b != null && !b;
//        }
//        return false;
//    }
//
//    @Override
//    protected boolean checkCandidate(String beanName, BeanDefinition candidate) {
//        return true;
//    }
//}
