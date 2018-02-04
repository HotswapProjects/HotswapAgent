package org.hotswap.agent.plugin.weld.command;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.servlet.http.HttpSession;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.BeanReloadStrategy;
import org.hotswap.agent.plugin.weld.WeldClassSignatureHelper;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;
import org.hotswap.agent.plugin.weld.transformer.CdiContextsTransformer;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.jboss.weld.bean.AbstractClassBean;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.attributes.BeanAttributesFactory;
import org.jboss.weld.context.AbstractBoundContext;
import org.jboss.weld.context.ContextNotActiveException;
import org.jboss.weld.context.PassivatingContextWrapper;
import org.jboss.weld.context.beanstore.BeanStore;
import org.jboss.weld.context.beanstore.BoundBeanStore;
import org.jboss.weld.context.beanstore.NamingScheme;
import org.jboss.weld.context.beanstore.http.EagerSessionBeanStore;
import org.jboss.weld.context.bound.BoundSessionContextImpl;
import org.jboss.weld.context.http.HttpSessionContextImpl;
import org.jboss.weld.context.http.HttpSessionDestructionContext;
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

    private static final Set<String> trackableSessionBasedScopes = new HashSet<>();

    static {
        trackableSessionBasedScopes.add("javax.enterprise.context.SessionScoped");
        trackableSessionBasedScopes.add("org.apache.deltaspike.core.api.scope.WindowScoped");
        trackableSessionBasedScopes.add("org.apache.deltaspike.core.api.scope.GroupedConversationScoped");
    }

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
            if (trackableSessionBasedScopes.contains(bean.getScope().getName())) {
                doReinjectSessionBasedBean(beanManager, beanClass, bean);
            } else {
                doReinjectBeanInstance(beanManager, beanClass, bean, beanManager.getContext(bean.getScope()));
            }
        } catch (ContextNotActiveException e) {
            LOGGER.info("No active contexts for bean '{}'", beanClass.getName());
        }
    }

    private static void doReinjectSessionBasedBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean) {
        Map<Class<? extends Annotation>, List<Context>> contexts = getContexts(beanManager);
        List<Context> contextList = contexts.get(SessionScoped.class);

        if (contextList != null && !contextList.isEmpty()) {
            for (Context ctx: contextList) {
                Context sessionCtx = PassivatingContextWrapper.unwrap(ctx);
                if (sessionCtx instanceof HttpSessionContextImpl) {
                    doReinjectHttpSessionBasedBean(beanManager, beanClass, bean, (HttpSessionContextImpl) sessionCtx);
                } else if (sessionCtx instanceof BoundSessionContextImpl) {
                    doReinjectBoundSessionBasedBean(beanManager, beanClass, bean, (BoundSessionContextImpl) sessionCtx);
                } else if (sessionCtx instanceof HttpSessionDestructionContext) {
                    // HttpSessionDestructionContext is temporary used for HttpSession context destruction, we don't handle it
                } else {
                    LOGGER.warning("Unexpected session context class '{}'.", sessionCtx.getClass().getName());
                }
            }
        } else {
            LOGGER.warning("No session context found in BeanManager.");
        }
    }

    private static void doReinjectBoundSessionBasedBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean,
            BoundSessionContextImpl sessionCtx) {

        if (sessionCtx.isActive()) {
            doReinjectIt(beanManager, beanClass, bean, sessionCtx);
        } else {
            BoundSessionBeanStoreRegistry beanStoreRegistry =
                    (BoundSessionBeanStoreRegistry) ReflectionHelper.get(sessionCtx, CdiContextsTransformer.BOUND_SESSION_BEAN_STORE_REGISTRY);
            if (beanStoreRegistry != null) {
                for (Map<String, Object> beanStore: beanStoreRegistry.getBeanStores()) {
                    try {
                        sessionCtx.associate(beanStore);
                        sessionCtx.activate();
                        doReinjectIt(beanManager, beanClass, bean, sessionCtx);
                    } finally {
                        // TODO : deactivate bean store withou destroy it
                    }
                }
            } else {
                LOGGER.error("Field '{}' not found in context class '{}'.", CdiContextsTransformer.BOUND_SESSION_BEAN_STORE_REGISTRY, sessionCtx.getClass().getName());
            }
        }
    }

    private static void doReinjectHttpSessionBasedBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean,
            AbstractBoundContext<?> sessionCtx) {
        NamingScheme namingScheme = (NamingScheme) ReflectionHelper.get(sessionCtx, "namingScheme");

        if (sessionCtx.isActive()) {
            doReinjectIt(beanManager, beanClass, bean, sessionCtx);
        } else {
            for (HttpSession session : HttpSessionsRegistry.getSessions()) {
                try {
                    setContextBeanStore(sessionCtx, new EagerSessionBeanStore(namingScheme, session));
                    sessionCtx.activate();
                    doReinjectIt(beanManager, beanClass, bean, sessionCtx);
                } finally {
                    try {
                        sessionCtx.deactivate();
                    } catch (Exception e) {
                        LOGGER.error("Context iterator close() failed.", e);
                    } finally {
                        setContextBeanStore(sessionCtx, null);
                    }
                }
            }
        }
    }

    private static void doReinjectIt(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean, AbstractBoundContext<?> sessionCtx) {
        if (bean.getScope().equals(SessionScoped.class)) {
            doReinjectBeanInstance(beanManager, beanClass, bean, sessionCtx);
        } else {
            doReinjectCustomScopedBean(beanManager, beanClass, bean, sessionCtx);
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

    private static void setContextBeanStore(AbstractBoundContext<?> sessionContext, BeanStore beanStore) {
        ReflectionHelper.invoke(sessionContext, AbstractBoundContext.class, "setBeanStore", new Class[] {BoundBeanStore.class}, beanStore);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doReinjectCustomScopedBean(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean<?> bean, Context parentContext) {

        // Get custom context tracker from map stored in session context
        Map trackerMap = (Map) ReflectionHelper.get(parentContext, CdiContextsTransformer.CUSTOM_CONTEXT_TRACKER_FIELD);
        if (trackerMap == null) {
            LOGGER.error("Custom context tracker field '{}' not found in context '{}'.", CdiContextsTransformer.CUSTOM_CONTEXT_TRACKER_FIELD,
                    parentContext.getClass().getName());
            return;
        }

        String scopeClassName = bean.getScope().getName();
        Object tracker = trackerMap.get(scopeClassName);

        if (tracker != null && tracker instanceof String) {
            scopeClassName = (String) tracker;
            tracker = trackerMap.get(scopeClassName);
        }

        if (tracker == null) {
            LOGGER.warning("Tracker for scope '{}' not found in context '{}'.", scopeClassName, parentContext);
            return;
        }

        if (! (tracker instanceof Iterable)) {
            LOGGER.error("Tracker '{}' is not Iterable.", tracker.getClass().getName());
            return;
        }

        Iterator<?> contextIterator = ((Iterable<?>) tracker).iterator();

        try {
            Class<? extends Annotation> scope =
                    (Class<? extends Annotation>) beanManager.getClass().getClassLoader().loadClass(scopeClassName);
            while (contextIterator.hasNext()) {
                contextIterator.next(); // Set active session context
                Context context = beanManager.getUnwrappedContext(scope);
                if (scopeClassName.equals(bean.getScope().getName())) {
                    doReinjectBeanInstance(beanManager, beanClass, bean, context);
                } else {
                    doReinjectCustomScopedBean(beanManager, beanClass, bean, context);
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Context iteration failed.", e);
        } finally {
            // iterator can implement closeable to finalize iteration
            if (contextIterator instanceof Closeable) {
                try {
                    ((Closeable) contextIterator).close();
                } catch (Exception e) {
                    LOGGER.error("Context iterator close() failed.", e);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectBeanInstance(BeanManagerImpl beanManager, Class<?> beanClass, AbstractClassBean bean, Context context) {
        Object get = context.get(bean);
        if (get != null) {
            bean.getProducer().inject(get, beanManager.createCreationalContext(bean));
            LOGGER.info("Bean '{}' injection points was reinjected.", beanClass.getName());
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
    private static void doDefineNewManagedBean(BeanManagerImpl beanManager, String bdaId,
            Class<?> beanClass) {
        try {
            ClassTransformer classTransformer = getClassTransformer();
            SlimAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
            boolean managedBeanOrDecorator = Beans.isTypeManagedBeanOrDecoratorOrInterceptor(annotatedType);

            if (managedBeanOrDecorator) {
                EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
                BeanAttributes attributes = BeanAttributesFactory.forBean(eat, beanManager);
                ManagedBean<?> bean = ManagedBean.of(attributes, eat, beanManager);
                Field field = beanManager.getClass().getDeclaredField("beanSet");
                field.setAccessible(true);
                field.set(beanManager, Collections.synchronizedSet(new HashSet<Bean<?>>()));
                // TODO:
                beanManager.addBean(bean);
                beanManager.getBeanResolver().clear();
                bean.initializeAfterBeanDiscovery();
                // define managed bean
                // beanManager.cleanupAfterBoot();
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
