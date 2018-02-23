package org.hotswap.agent.plugin.weld.command;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.weld.BeanReloadStrategy;
import org.hotswap.agent.plugin.weld.WeldClassSignatureHelper;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.jboss.weld.bean.AbstractClassBean;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.attributes.BeanAttributesFactory;
import org.jboss.weld.context.ContextNotActiveException;
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
     * @param reloadStrategy
     * @param oldSignatureByStrategy
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName
     */
    public static void reloadBean(String bdaId, Class<?> beanClass, String oldSignatureByStrategy, String strReloadStrategy) {

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

        doReloadBean(bdaId, beanClass, oldSignatureByStrategy, reloadStrategy);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "serial" })
    private static void doReloadBean(String bdaId, Class<?> beanClass, String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

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
                if (bean instanceof AbstractClassBean) {
                    EnhancedAnnotatedType eat = createAnnotatedTypeForExistingBeanClass(bdaId, beanClass);
                    if (!eat.isAbstract() || !eat.getJavaClass().isInterface()) { // injectionTargetCannotBeCreatedForInterface
                        ((AbstractClassBean)bean).setProducer(beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, bean, false));
                        if (isReinjectingContext(bean)) {
                            doReloadAbstractClassBean(beanManager, beanClass, (AbstractClassBean) bean, oldSignatureByStrategy, reloadStrategy);
                            LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
                            continue;
                        }
                    }
                    LOGGER.info("Bean '{}' redefined", beanClass.getName());
                } else {
                    LOGGER.warning("Bean '{}' reloading not supported.", beanClass.getName());
                }
            }
        } else {
            doDefineNewManagedBean(beanManager, bdaId, beanClass);
        }
    }

    private static boolean isReinjectingContext(Bean<?> bean) {
        return bean.getScope() != RequestScoped.class && bean.getScope() != Dependent.class;
    }

    private static EnhancedAnnotatedType<?> createAnnotatedTypeForExistingBeanClass(String bdaId, Class<?> beanClass) {
        ClassTransformer classTransformer = getClassTransformer();
        SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
        return EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
    }

    private static void doReloadAbstractClassBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        String signatureByStrategy = WeldClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (bean instanceof ManagedBean && (
                reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy)))
                ) {
            // Reload bean in contexts - invalidates existing instances
            doReloadBeanInBeanContexts(beanManager, beanClass, (ManagedBean<?>) bean);
        } else {
            // Reinjects bean instances in aproperiate contexts
            doReinjectBean(beanManager, beanClass, bean);
        }
    }

    private static void doReinjectBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean) {
        try {
            if (!bean.getScope().equals(ApplicationScoped.class) && HaCdiCommons.isRegisteredScope(bean.getScope())) {
                doReinjectRegisteredBeanInstances(beanManager, beanClass, bean);
            } else {
                doReinjectBeanInstance(beanManager, beanClass, bean, beanManager.getContext(bean.getScope()));
            }
        } catch (ContextNotActiveException e) {
            LOGGER.info("No active contexts for bean '{}'", beanClass.getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectRegisteredBeanInstances(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean bean) {
        for (Object instance: HaCdiCommons.getBeanInstances(bean)) {
            if (instance != null) {
                bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                LOGGER.info("Bean '{}' injection points was reinjected.", beanClass.getName());
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectBeanInstance(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean bean, Context context) {
        Object instance = context.get(bean);
        if (instance != null) {
            bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
            LOGGER.debug("Bean instance '{}' injection points was reinjected.", instance);
        }
    }

    private static void doReloadBeanInBeanContexts(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean<?> managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> contexts = getContextMap(beanManager);

            List<Context> ctxList = contexts.get(managedBean.getScope());

            if (ctxList != null) {
                for(Context context: ctxList) {
                    doReloadBeanInContext(beanManager, beanClass, managedBean, context);
                }
            } else {
                LOGGER.debug("No active contexts for bean '{}' in scope '{}'", beanClass.getName(),  managedBean.getScope());
            }
        } catch (ContextNotActiveException e) {
            LOGGER.warning("No active contexts for bean '{}'", e, beanClass.getName());
        } catch (Exception e) {
            LOGGER.warning("Context for '{}' failed to reload", e, beanClass.getName());
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
    private static void doReloadBeanInContext(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean, Context context) {
        if(ContextualReloadHelper.addToReloadSet(context, managedBean)) {
            LOGGER.debug("Bean {}, added to reload set in context '{}'", managedBean, context.getClass());
        } else {
            // fallback for not patched contexts
            doReinjectBean(beanManager, beanClass, managedBean);
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
