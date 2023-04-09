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
package org.hotswap.agent.plugin.owb_jakarta.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.decorator.Decorator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.Interceptor;

import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.config.BeansDeployer;
import org.apache.webbeans.config.BeansDeployer.ExtendedBeanAttributes;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.container.InjectionTargetFactoryImpl;
import org.apache.webbeans.portable.AbstractProducer;
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;
import org.apache.webbeans.proxy.OwbInterceptorProxy;
import org.apache.webbeans.proxy.OwbNormalScopeProxy;
import org.apache.webbeans.spi.BeanArchiveService.BeanArchiveInformation;
import org.apache.webbeans.spi.BeanArchiveService.BeanDiscoveryMode;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.owb_jakarta.BeanReloadStrategy;
import org.hotswap.agent.plugin.owb_jakarta.OwbClassSignatureHelper;
import org.hotswap.agent.plugin.owb_jakarta.beans.ContextualReloadHelper;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;
import org.hotswap.agent.util.signature.ClassSignatureElement;

/**
 * Handle definition and redefinition of bean classes in BeanManager. If the bean class already exists than, according reloading policy,
 * either bean instance re-injection or bean context reloading is processed.
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshAgent.class);

    /**
     * Reload bean in existing bean manager. Called by a reflection command from BeanRefreshCommand transformer.
     *
     * @param appClassLoader the application class loader
     * @param beanClassName the bean class name
     * @param oldFullSignatures the old full signatures
     * @param oldSignatures the map of class name to old signature
     * @param strReloadStrategy the bean reload strategy
     * @param beanArchiveUrl the bean archive url
     * @throws IOException error working with classDefinition
     */
    public static synchronized void reloadBean(ClassLoader appClassLoader, String beanClassName, Map<String, String> oldFullSignatures,
            Map<String, String> oldSignatures, String strReloadStrategy, URL beanArchiveUrl) throws IOException {
        try {
            BeanReloadStrategy reloadStrategy;

            try {
                reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
            } catch (Exception e) {
                reloadStrategy = BeanReloadStrategy.NEVER;
            }

            Class<?> beanClass = appClassLoader.loadClass(beanClassName);
            doReloadBean(appClassLoader, beanClass, oldFullSignatures, oldSignatures, reloadStrategy, beanArchiveUrl);

        } catch (ClassNotFoundException e) {
            LOGGER.error("Bean class '{}' not found.", e, beanClassName);
        }
    }

    @SuppressWarnings("serial")
    private static void doReloadBean(ClassLoader appClassLoader, Class<?> beanClass, Map<String, String> oldFullSignatures,
            Map<String, String> oldSignatures, BeanReloadStrategy reloadStrategy, URL beanArchiveUrl) {

        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(appClassLoader);

            // check if it is Object descendant
            if (Object.class.isAssignableFrom(beanClass)) {

                BeanManagerImpl beanManager = null;
                BeanManager bm = CDI.current().getBeanManager();

                if (bm instanceof BeanManagerImpl) {
                    beanManager = (BeanManagerImpl) bm;
                } else if (bm instanceof InjectableBeanManager){
                    beanManager = (BeanManagerImpl) ReflectionHelper.get(bm, "bm");
                }

                BeanArchiveInformation beanArchiveInfo =
                        beanManager.getWebBeansContext().getBeanArchiveService().getBeanArchiveInformation(beanArchiveUrl);

                if (!beanArchiveInfo.isClassExcluded(beanClass.getName())) {

                    Set<Bean<?>> beans = beanManager.getBeans(beanClass, new AnnotationLiteral<Any>() {});

                    if (beans != null && !beans.isEmpty()) {
                        for (Bean<?> bean : beans) {
                            if (bean.getBeanClass().isInterface()) {
                                continue;
                            }
                            if (!fullSignatureChanged(bean, oldFullSignatures)) {
                                LOGGER.debug("Skipping bean redefinition. Bean '{}' signature was not changed.", bean.getBeanClass().getName());
                                continue;
                            }

                            // just now only managed beans
                            if (bean instanceof InjectionTargetBean) {
                                createAnnotatedTypeForExistingBeanClass(beanManager, (InjectionTargetBean) bean);
                                if (isReinjectingContext(bean) || HaCdiCommons.isInExtraScope(bean)) {
                                    doReloadInjectionTargetBean(beanManager, (InjectionTargetBean) bean, oldSignatures, reloadStrategy);
                                    LOGGER.debug("Bean reloaded '{}'.", bean.getBeanClass().getName());
                                } else {
                                    LOGGER.info("Bean '{}' redefined.", bean.getBeanClass().getName());
                                }
                            }  else {
                                LOGGER.warning("Class '{}' is not InjectionTargetBean, reloading/reinjection not supported.", bean.getBeanClass().getName());
                            }
                        }
                    } else {
                        // Define new bean
                        doDefineNewBean(beanManager, beanClass, beanArchiveUrl);
                    }
                } else {
                    LOGGER.debug("Bean '{}' is excluded in BeanArchive.", beanClass.getName());
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    private static boolean fullSignatureChanged(Bean<?> bean, Map<String, String> oldFullSignatures) {

        try {
            String newSignature = ClassSignatureComparerHelper.getJavaClassSignature(bean.getBeanClass(), ClassSignatureElement.values());
            String oldSignature = oldFullSignatures.get(bean.getBeanClass().getName());
            return oldSignature != null && newSignature != null && !oldSignature.equals(newSignature);
        } catch (Exception e) {
            LOGGER.error("Full signature evaluation failed beanClass='{}'", e, bean.getBeanClass().getName());
        }
        return true;
    }

    private static boolean isReinjectingContext(Bean<?> bean) {
        return bean.getScope() != RequestScoped.class && bean.getScope() != Dependent.class;
    }

    private static void doReloadInjectionTargetBean(BeanManagerImpl beanManager, InjectionTargetBean<?> bean,
            Map<String, String> oldSignatures, BeanReloadStrategy reloadStrategy) {

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, bean.getBeanClass());
        String oldSignature = oldSignatures.get(bean.getBeanClass().getName());

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignature))) {
            // Reload bean in contexts - invalidates existing instances
            doReloadBeanInBeanContexts(beanManager, bean);
        } else {
            // keep beans in contexts, reinitialize bean injection points
            doReinjectBean(beanManager, bean);
        }
    }

    private static void doReinjectBean(BeanManagerImpl beanManager, InjectionTargetBean<?> bean) {
        try {
            if (!bean.getScope().equals(ApplicationScoped.class) &&
                    (!HaCdiCommons.isRegisteredScope(bean.getScope()) || HaCdiCommons.isInExtraScope(bean))) {
                doReinjectRegisteredBeanInstances(beanManager, bean);
            } else {
                doReinjectBeanInstance(beanManager, bean, beanManager.getContext(bean.getScope()));
            }
        } catch (ContextNotActiveException e) {
            LOGGER.info("No active contexts for bean '{}'", bean.getBeanClass().getName());
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void createAnnotatedTypeForExistingBeanClass(BeanManagerImpl beanManager, InjectionTargetBean bean) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches
        annotatedElementFactory.clear();

        Object forwardingMethIterceptors = null;

        if (bean.getProducer() instanceof AbstractProducer) {
            // methodInterceptors must be the same instance. It is stored in field owbIntDecHandler of existing
            // InterceptedProxy's instances
            try {
                forwardingMethIterceptors = ReflectionHelper.get(bean.getProducer(), "methodInterceptors");
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Field AbstractProducer.methodInterceptors is not accessible", e);
            }
        }

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(bean.getBeanClass());

        ReflectionHelper.set(bean, InjectionTargetBean.class, "annotatedType", annotatedType);

        // Updated members that were set by bean attributes
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "types", attributes.getTypes());
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "qualifiers", attributes.getQualifiers());
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "scope", attributes.getScope());
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "name", attributes.getName());
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "stereotypes", attributes.getStereotypes());
        ReflectionHelper.set(bean, BeanAttributesImpl.class, "alternative", attributes.isAlternative());

        InjectionTargetFactory factory = new InjectionTargetFactoryImpl(annotatedType, bean.getWebBeansContext());
        InjectionTarget injectionTarget = factory.createInjectionTarget(bean);
        ReflectionHelper.set(bean, InjectionTargetBean.class, "injectionTarget", injectionTarget);

        if (injectionTarget instanceof AbstractProducer) {
            if (forwardingMethIterceptors != null) {
                ReflectionHelper.set(injectionTarget, AbstractProducer.class, "methodInterceptors", forwardingMethIterceptors);
            }
        }

        LOGGER.debug("New annotated type created for bean '{}'", bean.getBeanClass());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectRegisteredBeanInstances(BeanManagerImpl beanManager, InjectionTargetBean bean) {
        for (Object instance: HaCdiCommons.getBeanInstances(bean)) {
            if (instance != null) {
                instance = unwrapInstance(beanManager, instance);
                bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                LOGGER.info("Bean '{}' injection points was reinjected.", bean.getBeanClass().getName());
            } else {
                LOGGER.info("Unexpected 'null' bean instance in registry. bean='{}'", bean.getBeanClass().getName());
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectBeanInstance(BeanManagerImpl beanManager, InjectionTargetBean bean, Context context) {
        Object instance = context.get(bean);
        if (instance != null) {
            instance = unwrapInstance(beanManager, instance);
            bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
            LOGGER.info("Bean '{}' injection points was reinjected.", bean.getBeanClass().getName());
        }
    }

    private static Object unwrapInstance(BeanManagerImpl beanManager, Object instance) {
        if (instance instanceof OwbNormalScopeProxy) {
            instance = NormalScopeProxyFactory.unwrapInstance(instance);
        }
        if (instance instanceof OwbInterceptorProxy) {
            instance = beanManager.getWebBeansContext().getInterceptorDecoratorProxyFactory().unwrapInstance(instance);
        }
        return instance;
    }

    private static void doReloadBeanInBeanContexts(BeanManagerImpl beanManager, InjectionTargetBean<?> bean) {
        try {
            Map<Class<? extends Annotation>, Context> singleContextMap = getSingleContextMap(beanManager);

            Context context = singleContextMap.get(bean.getScope());
            if (context != null) {
                doReloadBeanInContext(beanManager, bean, context);
            } else {
                Map<Class<? extends Annotation>, List<Context>> allContexts = getContextMap(beanManager);
                List<Context> ctxList = allContexts.get(bean.getScope());
                if (ctxList != null) {
                    for(Context ctx: ctxList) {
                        doReloadBeanInContext(beanManager, bean, ctx);
                    }
                } else {
                    LOGGER.debug("No active contexts for bean '{}' in scope '{}'", bean.getBeanClass().getName(),  bean.getScope());
                }
            }
        } catch (ContextNotActiveException e) {
            LOGGER.warning("No active contexts for bean '{}'", e, bean.getBeanClass().getName());
        } catch (Exception e) {
            LOGGER.warning("Context for '{}' failed to reload", e, bean.getBeanClass().getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Map<Class<? extends Annotation>, List<Context>> getContextMap(BeanManagerImpl beanManagerImpl){
        try {
            Field contextsField = BeanManagerImpl.class.getField("contextMap");
            contextsField.setAccessible(true);
            return (Map) contextsField.get(beanManagerImpl);
        } catch (IllegalAccessException |IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            LOGGER.warning("Field BeanManagerImpl.contextMap is not accessible", e);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Map<Class<? extends Annotation>, Context> getSingleContextMap(BeanManagerImpl beanManagerImpl){
        try {
            Field contextsField = BeanManagerImpl.class.getField("singleContextMap");
            contextsField.setAccessible(true);
            return (Map) contextsField.get(beanManagerImpl);
        } catch (IllegalAccessException |IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            LOGGER.warning("Field BeanManagerImpl.singleContextMap is not accessible", e);
        }
        return Collections.emptyMap();
    }

    private static void doReloadBeanInContext(BeanManagerImpl beanManager, InjectionTargetBean bean, Context context) {
        if (ContextualReloadHelper.addToReloadSet(context, bean)) {
            LOGGER.debug("Bean {}, added to reload set in context '{}'", bean, context.getClass());
        } else {
            // fallback: try to reinitialize injection points instead...
            doReinjectBeanInstance(beanManager, bean, context);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void doDefineNewBean(BeanManagerImpl beanManager, Class<?> beanClass, URL beanArchiveUrl) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches (is it necessary for definition ?)
        annotatedElementFactory.clear();

        // Injection resolver cache must be cleared before / after definition
        beanManager.getInjectionResolver().clearCaches();

        BeanArchiveInformation beanArchiveInfo =
                beanManager.getWebBeansContext().getBeanArchiveService().getBeanArchiveInformation(beanArchiveUrl);

        if (beanArchiveInfo.isClassExcluded(beanClass.getName())) {
            LOGGER.debug("Bean '{}' is excluded in BeanArchive.", beanClass.getName());
            return;
        }

        if (beanArchiveInfo.getBeanDiscoveryMode() == BeanDiscoveryMode.ANNOTATED) {
            if (beanClass.getAnnotations().length == 0 || !isCDIAnnotatedClass(beanManager, beanClass)) {
                LOGGER.debug("Class '{}' is not considered as bean for BeanArchive with bean-discovery-mode=\"annotated\"", beanClass.getName());
                return;
            }
        }

        AnnotatedType<?> annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);
        BeanAttributesImpl<?> attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        Map<AnnotatedType<?>, ExtendedBeanAttributes<?>> annotatedTypes = new HashMap<>();

        BeansDeployer beansDeployer = new BeansDeployer(wbc);

        try {
            // OWB 1.7
            ReflectionHelper.invoke(beansDeployer, BeansDeployer.class, "defineManagedBean",
                    new Class[] { jakarta.enterprise.inject.spi.AnnotatedType.class, BeanAttributes.class, java.util.Map.class },
                    annotatedType, attributes, annotatedTypes);
        } catch (Exception e) {
            try {
                // OWB 2.0
                ExtendedBeanAttributes extendedBeanAttributes =
                        ExtendedBeanAttributes.class.getConstructor(BeanAttributes.class, boolean.class, boolean.class)
                        .newInstance(attributes, false, false);
                ReflectionHelper.invoke(beansDeployer, BeansDeployer.class, "defineManagedBean",
                        new Class[] { jakarta.enterprise.inject.spi.AnnotatedType.class, ExtendedBeanAttributes.class, java.util.Map.class },
                        annotatedType, extendedBeanAttributes, annotatedTypes);
            } catch (Exception ex) {
                LOGGER.error("Bean '{}' definition failed.", beanClass.getName());
            }
        }
    }

    private static boolean isCDIAnnotatedClass(BeanManagerImpl beanManager, Class<?> beanClass) {
        for (Annotation annotation: beanClass.getAnnotations()) {
            if (isCDIAnnotation(beanManager, annotation.getClass())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCDIAnnotation(BeanManagerImpl beanManager, Class<? extends Annotation> annotation) {
        if (Interceptor.class.equals(annotation) || Decorator.class.equals(annotation)) {
            return true;
        }

        boolean isBeanAnnotation = beanManager.isScope(annotation);
        if (!isBeanAnnotation) {
            isBeanAnnotation = beanManager.isStereotype(annotation);
        }
        return isBeanAnnotation;
    }

}
