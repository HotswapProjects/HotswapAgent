package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
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
import org.apache.webbeans.spi.BeanArchiveService.BeanArchiveInformation;
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
     * @param beanClassName the bean class name
     * @param oldSignatureByStrategy the old signature by strategy
     * @param strReloadStrategy the bean reload strategy
     * @param beanArchiveUrl the bean archive url
     * @throws IOException error working with classDefinition
     */
    public static void reloadBean(ClassLoader appClassLoader, String beanClassName, String oldSignatureByStrategy,
            String strReloadStrategy, URL beanArchiveUrl) throws IOException {

        try {
            BeanReloadStrategy reloadStrategy;

            try {
                reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
            } catch (Exception e) {
                reloadStrategy = BeanReloadStrategy.NEVER;
            }

            Class<?> beanClass = appClassLoader.loadClass(beanClassName);

            doReloadBean(appClassLoader, beanClass, oldSignatureByStrategy, reloadStrategy, beanArchiveUrl);

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
     * @param beansXml the path to beans.xml
     */
    @SuppressWarnings("rawtypes")
    private static void doReloadBean(ClassLoader appClassLoader, Class<?> beanClass, String oldSignatureByStrategy,
            BeanReloadStrategy reloadStrategy, URL beanArchiveUrl) {

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

                if (beanManager != null) {

                    BeanArchiveInformation beanArchiveInfo =
                            beanManager.getWebBeansContext().getBeanArchiveService().getBeanArchiveInformation(beanArchiveUrl);

                    if (!beanArchiveInfo.isClassExcluded(beanClass.getName())) {

                        Set<Bean<?>> beans = beanManager.getBeans(beanClass);

                        if (beans != null && !beans.isEmpty()) {
                            for (Bean<?> bean : beans) {
                                // just now only managed beans
                                if (bean instanceof InjectionTargetBean) {
                                    doReloadInjectionTargetBean(beanManager, beanClass, (InjectionTargetBean) bean,
                                            oldSignatureByStrategy, reloadStrategy);
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
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doReloadInjectionTargetBean(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        createAnnotatedTypeForExistingBeanClass(beanManager, beanClass, bean);

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {

            // Reload bean in contexts - invalidates existing instances
            doReloadInjectionTargetBeanInContexts(beanManager, beanClass, bean);

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
                                Object instance = beanManager.getContext(bean.getScope()).get(bean);
                                if (instance != null) {
                                    bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                                    LOGGER.debug("Bean instance injection points are reinitialized '{}'", beanClass.getName());
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
                    Object instance = beanManager.getContext(bean.getScope()).get(bean);
                    if (instance != null) {
                        bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                        LOGGER.debug("Bean instance injection points are reinitialized '{}'", beanClass.getName());
                    }
                }
            } catch (javax.enterprise.context.ContextNotActiveException e) {
                LOGGER.warning("No active contexts for {}", beanClass.getName());
            }

        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void createAnnotatedTypeForExistingBeanClass(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches
        annotatedElementFactory.clear();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);

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

        LOGGER.debug("New annotated type created for beanClass {}", beanClass.getName());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void doReloadInjectionTargetBeanInContexts(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> allContexts = getContexts(beanManager);

            List<Context> ctxList = allContexts.get(bean.getScope());

            if(ctxList != null) {
                for(Context context: ctxList) {
                    if (context != null) {
                        LOGGER.debug("Inspecting context '{}' for bean class {}", context.getClass(), bean.getScope());
                        if(ContextualReloadHelper.addToReloadSet(context, bean)) {
                            LOGGER.debug("Bean {}, added to reload set in context {}", bean, context.getClass());
                        } else {
                            // try to reinitialize injection points instead...
                            try {
                                Object instance = context.get(bean);
                                if (instance != null) {
                                    bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                                    LOGGER.debug("Bean instance injection points are reinitialized '{}'", beanClass.getName());
                                }
                            } catch (Exception e) {
                                if(LOGGER.isLevelEnabled(Level.DEBUG)) {
                                    LOGGER.debug("Context {} not active for bean: {} in scope: {}",e, context.getClass(), beanClass.getName(), bean.getScope());
                                } else {
                                    LOGGER.warning("Context {} not active for bean: {} in scope: {}", context.getClass(), beanClass.getName(), bean.getScope());
                                }
                            }
                        }
                    } else {
                        LOGGER.debug("No active contexts for bean: {} in scope: {}",  bean.getScope(), beanClass.getName());
                    }
                }
            } else {
                LOGGER.debug("No active contexts for bean: {} in scope: {}",  bean.getScope(), beanClass.getName());
            }
        } catch (Exception e) {
            LOGGER.warning("Context for {} failed to reload", e, beanClass.getName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Map<Class<? extends Annotation>, List<Context>> getContexts(BeanManagerImpl beanManagerImpl){
        try {
            Field contextsField = BeanManagerImpl.class.getField("contextMap");
            contextsField.setAccessible(true);
            Map<Class<? extends Annotation>, List<Context>> ctxs= (Map) contextsField.get(beanManagerImpl);
            return ctxs;
        } catch (IllegalAccessException |IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            LOGGER.warning("BeanManagerImpl.contexts not accessible", e);
        }
        return Collections.emptyMap();
    }

}
