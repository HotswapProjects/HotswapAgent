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
package org.hotswap.agent.plugin.weld_jakarta.command;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.AnnotationLiteral;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.weld_jakarta.BeanReloadStrategy;
import org.hotswap.agent.plugin.weld_jakarta.WeldClassSignatureHelper;
import org.hotswap.agent.plugin.weld_jakarta.beans.ContextualReloadHelper;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;
import org.hotswap.agent.util.signature.ClassSignatureElement;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.jboss.weld.bean.AbstractClassBean;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.attributes.BeanAttributesFactory;
// import org.jboss.weld.context.ContextNotActiveException;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.manager.api.WeldManager;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.resources.ReflectionCache;
import org.jboss.weld.resources.ReflectionCacheFactory;
import org.jboss.weld.resources.SharedObjectCache;
import org.jboss.weld.util.Beans;

public class BeanReloadExecutor {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanReloadExecutor.class);

    /**
     * Reload bean in existing bean manager.
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClass the bean class
     * @param oldFullSignatures the old full signatures
     * @param oldSignatures the old signatures
     * @param strReloadStrategy the str reload strategy
     */
    public static void reloadBean(String bdaId, Class<?> beanClass, Map<String, String> oldFullSignatures,
            Map<String, String> oldSignatures, String strReloadStrategy) {

        BeanReloadStrategy reloadStrategy;

        // check if it is Object descendant (not interface)
        if (!Object.class.isAssignableFrom(beanClass)) {
            return;
        }

        try {
            reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
        } catch (Exception e) {
            reloadStrategy = BeanReloadStrategy.NEVER;
        }

        doReloadBean(bdaId, beanClass, oldFullSignatures, oldSignatures, reloadStrategy);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "serial" })
    private static void doReloadBean(String bdaId, Class<?> beanClass, Map<String, String> oldFullSignatures,
            Map<String, String> oldSignatures, BeanReloadStrategy reloadStrategy) {

        BeanManagerImpl beanManager = null;
        BeanManager bm = CDI.current().getBeanManager();

        if (bm instanceof WeldManager) {
            bm = ((WeldManager) bm).unwrap();
        }

        if (bm instanceof BeanManagerImpl) {
            beanManager = (BeanManagerImpl) bm;
        }

        // TODO: check if archive is excluded

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
                if (bean instanceof AbstractClassBean) {
                    EnhancedAnnotatedType eat = createAnnotatedTypeForExistingBeanClass(bdaId, bean);
                    if (!eat.isAbstract() || !eat.getJavaClass().isInterface()) { // injectionTargetCannotBeCreatedForInterface
                        ((AbstractClassBean)bean).setProducer(beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, bean, false));
                        if (isReinjectingContext(bean) || HaCdiCommons.isInExtraScope(bean)) {
                            doReloadAbstractClassBean(beanManager, (AbstractClassBean) bean, oldSignatures, reloadStrategy);
                            LOGGER.debug("Bean reloaded '{}'", bean.getBeanClass().getName());
                            continue;
                        }
                    }
                    LOGGER.info("Bean '{}' redefined", bean.getBeanClass().getName());
                } else {
                    LOGGER.warning("Bean '{}' is not AbstractClassBean, reloading/reinjection not supported.", bean.getBeanClass().getName());
                }
            }
        } else {
            doDefineNewManagedBean(beanManager, bdaId, beanClass);
        }
    }

    private static boolean isReinjectingContext(Bean<?> bean) {
        return bean.getScope() != RequestScoped.class && bean.getScope() != Dependent.class;
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

    private static EnhancedAnnotatedType<?> createAnnotatedTypeForExistingBeanClass(String bdaId, Bean<?> bean) {
        ClassTransformer classTransformer = getClassTransformer();
        SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(bean.getBeanClass(), bdaId);
        return EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
    }

    private static void doReloadAbstractClassBean(BeanManagerImpl beanManager, AbstractClassBean<?> bean, Map<String, String> oldSignatures, BeanReloadStrategy reloadStrategy) {

        String signatureByStrategy = WeldClassSignatureHelper.getSignatureByStrategy(reloadStrategy, bean.getBeanClass());

        String oldSignature = oldSignatures.get(bean.getBeanClass().getName());

        if (bean instanceof ManagedBean && (
                reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignature)))
                ) {
            // Reload bean in contexts - invalidates existing instances
            doReloadBeanInBeanContexts(beanManager, (ManagedBean<?>) bean);
        } else {
            // Reinjects bean instances in aproperiate contexts
            doReinjectBean(beanManager, bean);
        }
    }

    private static void doReinjectBean(BeanManagerImpl beanManager, AbstractClassBean<?> bean) {
        try {
            if (!bean.getScope().equals(ApplicationScoped.class) &&
                    (HaCdiCommons.isRegisteredScope(bean.getScope()) || HaCdiCommons.isInExtraScope(bean))) {
                doReinjectRegisteredBeanInstances(beanManager, bean);
            } else {
                doReinjectBeanInstance(beanManager, bean, beanManager.getContext(bean.getScope()));
            }
        } catch (Exception e) {
            if (e.getClass().getSimpleName().equals("ContextNotActiveException")) {
                LOGGER.info("No active contexts for bean '{}'", bean.getBeanClass().getName());
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void doReinjectRegisteredBeanInstances(BeanManagerImpl beanManager, AbstractClassBean bean) {
        for (Object instance: HaCdiCommons.getBeanInstances(bean)) {
            if (instance != null) {
                doCallInject(beanManager, bean, instance);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectBeanInstance(BeanManagerImpl beanManager, AbstractClassBean bean, Context context) {
        Object instance = context.get(bean);
        if (instance != null) {
            doCallInject(beanManager, bean, instance);
        }
    }

    @SuppressWarnings("unchecked")
    private static void doCallInject(BeanManagerImpl beanManager, AbstractClassBean bean, Object instance) {

        // In whatever reason, we have to use reflection call for beanManager.createCreationalContext() in weld>=3.0
        Method m = null;
        try {
            m = beanManager.getClass().getMethod("createCreationalContext", Contextual.class);
        } catch (Exception e) {
            LOGGER.error("BeanManager.createCreationalContext() method not found beanManagerClass='{}'", e, bean.getBeanClass().getName());
            return;
        }

        try {
            bean.getProducer().inject(instance, (CreationalContext) m.invoke(beanManager, bean));
            LOGGER.debug("Bean instance '{}' injection points was reinjected.", instance);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            LOGGER.error("beanManager.createCreationalContext(beanManager, bean) invocation failed beanManagerClass='{}', beanClass='{}'", e,
                    bean.getBeanClass().getName(), bean.getClass().getName());
        }
    }

    private static void doReloadBeanInBeanContexts(BeanManagerImpl beanManager, ManagedBean<?> managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> contexts = getContextMap(beanManager);

            List<Context> ctxList = contexts.get(managedBean.getScope());

            if (ctxList != null) {
                for(Context context: ctxList) {
                    doReloadBeanInContext(beanManager, managedBean, context);
                }
            } else {
                LOGGER.debug("No active contexts for bean '{}' in scope '{}'", managedBean.getBeanClass().getName(),  managedBean.getScope());
            }
        } catch (Exception e) {
            if (e.getClass().getSimpleName().equals("ContextNotActiveException")) {
                LOGGER.warning("No active contexts for bean '{}'", e, managedBean.getBeanClass().getName());
            } else {
                LOGGER.warning("Context for '{}' failed to reload", e, managedBean.getBeanClass().getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<? extends Annotation>, List<Context>> getContextMap(BeanManagerImpl beanManager) {
        try {
            return Map.class.cast(ReflectionHelper.get(beanManager, "contexts"));
        } catch (Exception e) {
            LOGGER.warning("BeanManagerImpl.contexts not accessible", e);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReloadBeanInContext(BeanManagerImpl beanManager, ManagedBean managedBean, Context context) {
        if(ContextualReloadHelper.addToReloadSet(context, managedBean)) {
            LOGGER.debug("Bean {}, added to reload set in context '{}'", managedBean, context.getClass());
        } else {
            // fallback for not patched contexts
            doReinjectBean(beanManager, managedBean);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doDefineNewManagedBean(BeanManagerImpl beanManager, String bdaId, Class<?> beanClass) {
        try {
            ClassTransformer classTransformer = getClassTransformer();
            SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
            boolean managedBeanOrDecorator = Beans.isTypeManagedBeanOrDecoratorOrInterceptor(annotatedType);

            if (managedBeanOrDecorator) {
                EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
                BeanAttributes attributes = BeanAttributesFactory.forBean(eat, beanManager);
                ManagedBean<?> bean = ManagedBean.of(attributes, eat, beanManager);
                ReflectionHelper.set(beanManager, beanManager.getClass(), "beanSet", Collections.synchronizedSet(new HashSet<Bean<?>>()));
                beanManager.addBean(bean);
                beanManager.getBeanResolver().clear();
                bean.initializeAfterBeanDiscovery();
                LOGGER.debug("Bean defined '{}'", beanClass.getName());
            } else {
                // TODO : define session bean
                LOGGER.warning("Bean NOT? defined '{}', session bean?", beanClass.getName());
            }
        } catch (Exception e) {
            LOGGER.debug("Bean definition failed.", e);
        }
    }

    private static ClassTransformer getClassTransformer() {
        TypeStore store = new TypeStore();
        SharedObjectCache cache = new SharedObjectCache();
        ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
        ClassTransformer classTransformer = new ClassTransformer(store, cache, reflectionCache, "STATIC_INSTANCE");
        return classTransformer;
    }

}
