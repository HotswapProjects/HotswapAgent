package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.InjectionTargetFactory;

import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.container.InjectionTargetFactoryImpl;
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.web.context.WebContextsService;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.plugin.owb.BeanReloadStrategy;
import org.hotswap.agent.plugin.owb.OwbClassSignatureHelper;
import org.hotswap.agent.plugin.owb.WebBeansContextsServiceTransformer;
import org.hotswap.agent.plugin.owb.beans.ContextualReloadHelper;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Handles creating and redefinition of bean classes in BeanArchive
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshAgent.class);

    /** True for UnitTests */
    public static boolean isTestEnvironment = false;

    /**
     * Flag to check the reload status. In unit test we need to wait for reload
     * finishing before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    /**
     * Called by a reflection command from BeanRefreshCommand transformer.
     *
     * @param appClassLoader the application class loader
     * @param archivePath the archive path
     * @param beanClassName the bean class name
     * @param oldSignatureByStrategy the old signature by strategy
     * @param strReloadStrategy the bean reload strategy
     * @throws IOException error working with classDefinition
     */
    public static void reloadBean(ClassLoader appClassLoader, String beanClassName, String oldSignatureByStrategy, String strReloadStrategy) throws IOException {

        try {
            BeanReloadStrategy reloadStrategy;

            try {
                reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
            } catch (Exception e) {
                reloadStrategy = BeanReloadStrategy.NEVER;
            }

            Class<?> beanClass = appClassLoader.loadClass(beanClassName);

            doReloadBean(appClassLoader, beanClass, oldSignatureByStrategy, reloadStrategy);

        } catch (ClassNotFoundException e) {
            LOGGER.error("Bean class not found.", e);
        } finally {
            reloadFlag = false;
        }
    }

    /**
     * Reload bean in existing bean manager.
     *
     * @param appClassLoader the class loader
     * @param beanClass the bean class
     * @param oldSignatureByStrategy the old signature by strategy
     * @param reloadStrategy the reload strategy
     */
    @SuppressWarnings("rawtypes")
    private static void doReloadBean(ClassLoader appClassLoader, Class<?> beanClass, String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

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

                Set<Bean<?>> beans = beanManager.getBeans(beanClass);

                if (beans != null && !beans.isEmpty()) {
                    for (Bean<?> bean : beans) {
                        // just now only managed beans
                        if (bean instanceof InjectionTargetBean) {
                            doReloadInjectionTargetBean(beanManager, beanClass, (InjectionTargetBean) bean, oldSignatureByStrategy, reloadStrategy);
                        } else {
                            LOGGER.warning("reloadBean() : class '{}' reloading is not implemented ({}).",
                                    bean.getClass().getName(), bean.getBeanClass());
                        }
                    }
                    LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
                } else {
                    // Create new bean
                    HaBeanDeployer.doDefineManagedBean(beanManager, beanClass);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doReloadInjectionTargetBean(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean injTargetBean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        createAnnotatedTypeForExistingBeanClass(beanManager, beanClass, injTargetBean);

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {

            // Reload bean in contexts - invalidates existing instances
            doReloadInjectionTargetBeanInContexts(beanManager, beanClass, injTargetBean);

        } else {

            // keep beans in contexts, reinitialize bean injection points
            try {
                WebBeansContext wbc = beanManager.getWebBeansContext();
                ContextsService contextsService = wbc.getContextsService();

                if (!isTestEnvironment && contextsService instanceof WebContextsService) {

                    // For WebContextService(web application) iterate over all combination of context

                    // WebContextsTracker can't be directly used here, since it can be in different class loaders (Tomee)
                    // so we can use inner Iterator as workaround
                    Object ctxTracker = ReflectionHelper.get(contextsService, WebBeansContextsServiceTransformer.CONTEXT_TRACKER_FLD_NAME);

                    if (ctxTracker != null) {
                        try {
                            // iterate over contexts combination
                            Iterator it = ((Iterable ) ctxTracker).iterator();
                            while (it.hasNext()) {
                                it.next();
                                Object get = beanManager.getContext(injTargetBean.getScope()).get(injTargetBean);
                                if (get != null) {
                                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                                    injTargetBean.getProducer().inject(get, beanManager.createCreationalContext(injTargetBean));
                                }
                            }
                        } finally {
                            contextsService.removeThreadLocals();
                        }
                    } else {
                        LOGGER.error("ContextTracker not found on class '{}'", contextsService.getClass().getName());
                    }
                } else {
                    // For DefaultContextdService and testEnviroment use current contexts
                    Object get = beanManager.getContext(injTargetBean.getScope()).get(injTargetBean);
                    if (get != null) {
                        LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                        injTargetBean.getProducer().inject(get, beanManager.createCreationalContext(injTargetBean));
                    }
                }
            } catch (javax.enterprise.context.ContextNotActiveException e) {
                LOGGER.warning("No active contexts for {}", beanClass.getName());
            }

        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void createAnnotatedTypeForExistingBeanClass(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean injTargetBean) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches
        annotatedElementFactory.clear();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);

        ReflectionHelper.set(injTargetBean, InjectionTargetBean.class, "annotatedType", annotatedType);

        // Updated members that were set by bean attributes
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "types", attributes.getTypes());
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "qualifiers", attributes.getQualifiers());
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "scope", attributes.getScope());
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "name", attributes.getName());
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "stereotypes", attributes.getStereotypes());
        ReflectionHelper.set(injTargetBean, BeanAttributesImpl.class, "alternative", attributes.isAlternative());

        InjectionTargetFactory factory = new InjectionTargetFactoryImpl(annotatedType, injTargetBean.getWebBeansContext());
        InjectionTarget injectionTarget = factory.createInjectionTarget(injTargetBean);
        ReflectionHelper.set(injTargetBean, InjectionTargetBean.class, "injectionTarget", injectionTarget);

        LOGGER.debug("New annotated type created for beanClass {}", beanClass.getName());
    }

    @SuppressWarnings("rawtypes")
    private static void doReloadInjectionTargetBeanInContexts(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean injTargetBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> allContexts = getContexts(beanManager);

            List<Context> ctxList = allContexts.get(injTargetBean.getScope());

            if(ctxList != null) {
                for(Context context: ctxList) {
                    if (context != null) {
                        LOGGER.debug("Inspecting context '{}' for bean class {}", context.getClass(), injTargetBean.getScope());
                        if(ContextualReloadHelper.addToReloadSet(context, injTargetBean)) {
                            LOGGER.debug("Bean {}, added to reload set in context {}", injTargetBean, context.getClass());
                        } else {
                            // try to reinitialize injection points instead...
                            try {
                                Object get = context.get(injTargetBean);
                                if (get != null) {
                                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                                    injTargetBean.getProducer().inject(get, beanManager.createCreationalContext(injTargetBean));
                                }
                            } catch (Exception e) {
                                if(LOGGER.isLevelEnabled(Level.DEBUG)) {
                                    LOGGER.debug("Context {} not active for bean: {} in scope: {}",e, context.getClass(), beanClass.getName(), injTargetBean.getScope());
                                } else {
                                    LOGGER.warning("Context {} not active for bean: {} in scope: {}", context.getClass(), beanClass.getName(), injTargetBean.getScope());
                                }
                            }
                        }
                    } else {
                        LOGGER.debug("No active contexts for bean: {} in scope: {}",  injTargetBean.getScope(), beanClass.getName());
                    }
                }
            } else {
                LOGGER.debug("No active contexts for bean: {} in scope: {}",  injTargetBean.getScope(), beanClass.getName());
            }
        } catch (Exception e) {
            LOGGER.warning("Context for {} failed to reload", e, beanClass.getName());
        }
    }

    private static Map<Class<? extends Annotation>, List<Context>> getContexts(BeanManagerImpl beanManagerImpl){
        try {
            Field contextsField = BeanManagerImpl.class.getField("contextMap");
            contextsField.setAccessible(true);
            Map<Class<? extends Annotation>, List<Context>> ctxs= Map.class.cast(contextsField.get(beanManagerImpl));
            return ctxs;
        } catch (IllegalAccessException |IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            LOGGER.warning("BeanManagerImpl.contexts not accessible", e);
        }
        return Collections.emptyMap();
    }

}
