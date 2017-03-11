package org.hotswap.agent.plugin.weld.command;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpSession;

import org.hotswap.agent.logging.AgentLogger;
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
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.context.AbstractBoundContext;
import org.jboss.weld.context.PassivatingContextWrapper;
import org.jboss.weld.context.beanstore.BeanStore;
import org.jboss.weld.context.beanstore.BoundBeanStore;
import org.jboss.weld.context.beanstore.NamingScheme;
import org.jboss.weld.context.beanstore.http.EagerSessionBeanStore;
import org.jboss.weld.context.http.HttpSessionContextImpl;
import org.jboss.weld.manager.BeanManagerImpl;
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
     * @param reloadStrategy
     * @param oldSignatureByStrategy
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName
     */
    @SuppressWarnings("rawtypes")
    public static void reloadBean(String bdaId, Class<?> beanClass, String oldSignatureByStrategy, String strReloadStrategy) {

        // check if it is Object descendant (not interface)
        if (!Object.class.isAssignableFrom(beanClass)) {
            return;
        }

        BeanReloadStrategy reloadStrategy;

        try {
            reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
        } catch (Exception e) {
            reloadStrategy = BeanReloadStrategy.NEVER;
        }

        BeanManagerImpl beanManager;
        if (CDI.current().getBeanManager() instanceof BeanManagerImpl) {
            beanManager = ((BeanManagerImpl) CDI.current().getBeanManager()).unwrap();
        } else {
            beanManager = ((BeanManagerProxy) CDI.current().getBeanManager()).unwrap();
        }

        Set<Bean<?>> beans = beanManager.getBeans(beanClass);

        if (beans != null && !beans.isEmpty()) {
            for (Bean<?> bean : beans) {
                if (bean instanceof AbstractClassBean) {
                    doReloadAbstractClassBean(beanManager, bdaId, beanClass, (AbstractClassBean) bean, oldSignatureByStrategy, reloadStrategy);
                } else {
                    LOGGER.warning("reloadBean() : class '{}' reloading is not implemented ({}).",
                            bean.getClass().getName(), bean.getBeanClass());
                }
            }
            LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
        } else {
            try {
                ClassTransformer classTransformer = getClassTransformer();
                SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
                boolean managedBeanOrDecorator = Beans.isTypeManagedBeanOrDecoratorOrInterceptor(annotatedType);

                if (managedBeanOrDecorator) {
                    EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
                    defineManagedBean(beanManager, eat);
                    // define managed bean
                    // beanManager.cleanupAfterBoot();
                    LOGGER.debug("Bean defined '{}'", beanClass.getName());
                } else {
                    // TODO : define session bean
                    LOGGER.warning("Bean NOT? defined '{}', session bean?", beanClass.getName());
                }
            } catch (Exception e) {
                LOGGER.debug("Bean definition failed", e);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doReloadAbstractClassBean(BeanManagerImpl beanManager, String bdaId, Class<?> beanClass,
            AbstractClassBean bean, String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        EnhancedAnnotatedType eat = createAnnotatedTypeForExistingBeanClass(bdaId, beanClass);

        if (!eat.isAbstract() || !eat.getJavaClass().isInterface()) { // injectionTargetCannotBeCreatedForInterface

            bean.setProducer(beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, bean, false));

            String signatureByStrategy = WeldClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

            if (bean instanceof ManagedBean && (
                  reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                 (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy)))
                 ) {
                // Reload bean in contexts - invalidates existing instances
                doReloadManagedBeanInContexts(beanManager, beanClass, (ManagedBean) bean);

            } else {
                // Reinjects bean instances in aproperiate contexts
                doReinjectAbstractClassBeanInstances(beanManager, beanClass, bean);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectAbstractClassBeanInstances(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean bean) {
        // keep beans in contexts, reinitialize bean injection points
        try {
            Map<Class<? extends Annotation>, List<Context>> contexts = getContexts(beanManager);
            List<Context> ctx = contexts.get(bean.getScope());
            if (ctx != null) {
                for (Context context : ctx) {
                    if (context.isActive()) {
                        Object get = context.get(bean);
                        if (get != null) {
                            LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                            bean.getProducer().inject(get, beanManager.createCreationalContext(bean));
                        }
                    } else {
                        context = PassivatingContextWrapper.unwrap(context);
                        if (context.getScope().equals(SessionScoped.class) && context instanceof HttpSessionContextImpl) {
                            doReinjectInSessionCtx(beanManager, beanClass, bean, context);
                        }
                    }
                }
            }

        } catch (org.jboss.weld.context.ContextNotActiveException e) {
            LOGGER.warning("No active contexts for {}", beanClass.getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectInSessionCtx(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean bean, Context context) {
        HttpSessionContextImpl sessionContext = (HttpSessionContextImpl) context;
        List<HttpSession> seenSessions = HttpSessionsRegistry.getSeenSessions();
        for (HttpSession session : seenSessions) {
            BeanStore beanStore = null;
            try {
                NamingScheme namingScheme = (NamingScheme) ReflectionHelper.get(sessionContext, "namingScheme");
                beanStore = new EagerSessionBeanStore(namingScheme, session);
                ReflectionHelper.invoke(sessionContext, AbstractBoundContext.class, "setBeanStore", new Class[] {BoundBeanStore.class}, beanStore);
                sessionContext.activate();
                Object get = sessionContext.get(bean);
                if (get != null) {
                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                    bean.getProducer().inject(get, beanManager.createCreationalContext(bean));
                }

            } finally {
                try {
                    sessionContext.deactivate();
                } catch (Exception e) {
                }
                if (beanStore != null) {
                    try {
                        ReflectionHelper.invoke(sessionContext, AbstractBoundContext.class, "setBeanStore", new Class[] {BeanStore.class}, (BeanStore) null);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReloadManagedBeanInContexts(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> contexts = getContexts(beanManager);

            List<Context> ctxList = contexts.get(managedBean.getScope());

            if (ctxList != null) {
                for(Context context: ctxList) {
                    if (context != null) {
                        LOGGER.debug("Inspecting context '{}' for bean class {}", context.getClass(), managedBean.getScope());
                        if(ContextualReloadHelper.addToReloadSet(context, managedBean)) {
                            LOGGER.debug("Bean {}, added to reload set in context {}", managedBean, context.getClass());
                        } else {
                            // fallback for not patched contexts
                            doReinjectAbstractClassBeanInstances(beanManager, beanClass, managedBean);
                        }
                    } else {
                        LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
                    }
                }
            } else {
                LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
            }
        } catch (org.jboss.weld.context.ContextNotActiveException e) {
            LOGGER.warning("No active contexts for {}", e, beanClass.getName());
        } catch (Exception e) {
            LOGGER.warning("Context for {} failed to reload", e, beanClass.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<? extends Annotation>, List<Context>> getContexts(BeanManagerImpl beanManager){
        try {
            return Map.class.cast(ReflectionHelper.get(beanManager, "contexts"));
        } catch (Exception e) {
            LOGGER.warning("BeanManagerImpl.contexts not accessible", e);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void defineManagedBean(BeanManagerImpl beanManager, EnhancedAnnotatedType eat) throws Exception {
        BeanAttributes attributes = BeanAttributesFactory.forBean(eat, beanManager);
        ManagedBean<?> bean = ManagedBean.of(attributes, eat, beanManager);
        Field field = beanManager.getClass().getDeclaredField("beanSet");
        field.setAccessible(true);
        field.set(beanManager, Collections.synchronizedSet(new HashSet<Bean<?>>()));
        // TODO:
        beanManager.addBean(bean);
        beanManager.getBeanResolver().clear();
        bean.initializeAfterBeanDiscovery();
    }

    private static EnhancedAnnotatedType<?> createAnnotatedTypeForExistingBeanClass(String bdaId, Class<?> beanClass) {
        ClassTransformer classTransformer = getClassTransformer();
        SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
        return EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
    }

    private static ClassTransformer getClassTransformer() {
        TypeStore store = new TypeStore();
        SharedObjectCache cache = new SharedObjectCache();
        ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
        ClassTransformer classTransformer = new ClassTransformer(store, cache, reflectionCache, "STATIC_INSTANCE");
        return classTransformer;
    }

}
