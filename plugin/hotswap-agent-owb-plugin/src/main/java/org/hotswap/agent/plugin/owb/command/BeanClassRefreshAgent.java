package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.InjectionTargetFactory;
import javax.enterprise.util.AnnotationLiteral;

import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.config.BeansDeployer;
import org.apache.webbeans.config.BeansDeployer.ExtendedBeanAttributes;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.container.InjectionTargetFactoryImpl;
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.spi.BeanArchiveService.BeanArchiveInformation;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.owb.BeanReloadStrategy;
import org.hotswap.agent.plugin.owb.OwbClassSignatureHelper;
import org.hotswap.agent.plugin.owb.beans.ContextualReloadHelper;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Handle definition and redefinition of bean classes in BeanManager. If the bean class already exists than, according reloading policy,
 * either bean instance re-injection or bean context reloading is processed.
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshAgent.class);

    /**
     * Flag for checking reload status. It is used in unit tests for waiting for reload finish.
     * Set flag to true in the unit test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    /**
     * Reload bean in existing bean manager. Called by a reflection command from BeanRefreshCommand transformer.
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
            LOGGER.error("Bean class '{}' not found.", e, beanClassName);
        } finally {
            reloadFlag = false;
        }
    }

    @SuppressWarnings("serial")
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

                BeanArchiveInformation beanArchiveInfo =
                        beanManager.getWebBeansContext().getBeanArchiveService().getBeanArchiveInformation(beanArchiveUrl);

                if (!beanArchiveInfo.isClassExcluded(beanClass.getName())) {

                    Set<Bean<?>> beans = beanManager.getBeans(beanClass, new AnnotationLiteral<Any>() {});

                    if (beans != null && !beans.isEmpty()) {
                        for (Bean<?> bean : beans) {
                            // just now only managed beans
                            if (bean instanceof InjectionTargetBean) {
                                createAnnotatedTypeForExistingBeanClass(beanManager, beanClass, (InjectionTargetBean) bean);
                                if (isReinjectingContext(bean)) {
                                    doReloadInjectionTargetBean(beanManager, beanClass, (InjectionTargetBean) bean, oldSignatureByStrategy, reloadStrategy);
                                    LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
                                } else {
                                    LOGGER.info("Bean '{}' redefined", beanClass.getName());
                                }
                            }  else {
                                LOGGER.warning("Class '{}' reloading not supported.", beanClass.getName());
                            }
                        }
                    } else {
                        // Define new bean
                        doDefineNewBean(beanManager, beanClass);
                    }
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    private static boolean isReinjectingContext(Bean<?> bean) {
        return bean.getScope() != RequestScoped.class && bean.getScope() != Dependent.class;
    }

    private static void doReloadInjectionTargetBean(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean<?> bean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {
            // Reload bean in contexts - invalidates existing instances
            doReloadBeanInBeanContexts(beanManager, beanClass, bean);
        } else {
            // keep beans in contexts, reinitialize bean injection points
            doReinjectBean(beanManager, beanClass, bean);
        }
    }

    private static void doReinjectBean(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean<?> bean) {
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

        LOGGER.debug("New annotated type created for bean '{}'", beanClass.getName());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectRegisteredBeanInstances(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean) {
        for (Object instance: HaCdiCommons.getBeanInstances(bean)) {
            if (instance != null) {
                bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
                LOGGER.info("Bean '{}' injection points was reinjected.", beanClass.getName());
            } else {
                LOGGER.info("Unexpected 'null' bean instance in registry. bean='{}'", beanClass.getName());
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void doReinjectBeanInstance(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean, Context context) {
        Object instance = context.get(bean);
        if (instance != null) {
            bean.getProducer().inject(instance, beanManager.createCreationalContext(bean));
            LOGGER.info("Bean '{}' injection points was reinjected.", beanClass.getName());
        }
    }

    private static void doReloadBeanInBeanContexts(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean<?> bean) {
        try {
            Map<Class<? extends Annotation>, Context> singleContextMap = getSingleContextMap(beanManager);

            Context context = singleContextMap.get(bean.getScope());
            if (context != null) {
                doReloadBeanInContext(beanManager, beanClass, bean, context);
            } else {
                Map<Class<? extends Annotation>, List<Context>> allContexts = getContextMap(beanManager);
                List<Context> ctxList = allContexts.get(bean.getScope());
                if (ctxList != null) {
                    for(Context ctx: ctxList) {
                        doReloadBeanInContext(beanManager, beanClass, bean, ctx);
                    }
                } else {
                    LOGGER.debug("No active contexts for bean '{}' in scope '{}'", beanClass.getName(),  bean.getScope());
                }
            }
        } catch (ContextNotActiveException e) {
            LOGGER.warning("No active contexts for bean '{}'", e, beanClass.getName());
        } catch (Exception e) {
            LOGGER.warning("Context for '{}' failed to reload", e, beanClass.getName());
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

    private static void doReloadBeanInContext(BeanManagerImpl beanManager, Class<?> beanClass, InjectionTargetBean bean, Context context) {
        if (ContextualReloadHelper.addToReloadSet(context, bean)) {
            LOGGER.debug("Bean {}, added to reload set in context '{}'", bean, context.getClass());
        } else {
            // fallback: try to reinitialize injection points instead...
            doReinjectBeanInstance(beanManager, beanClass, bean, context);
        }
    }

    private static void doDefineNewBean(BeanManagerImpl beanManager, Class<?> beanClass) {
        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches (is it necessary for definition ?)
        annotatedElementFactory.clear();

        // Injection resolver cache must be cleared before / after definition
        beanManager.getInjectionResolver().clearCaches();

        AnnotatedType<?> annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);
        BeanAttributesImpl<?> attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        HashMap<AnnotatedType<?>, ExtendedBeanAttributes<?>> annotatedTypes = new HashMap<>();

        BeansDeployer beansDeployer = new BeansDeployer(wbc);

        try {
            ReflectionHelper.invoke(beansDeployer, BeansDeployer.class, "defineManagedBean",
                    new Class[] { javax.enterprise.inject.spi.AnnotatedType.class, javax.enterprise.inject.spi.BeanAttributes.class, java.util.Map.class },
                    annotatedType, attributes, annotatedTypes);
        } catch (Exception e) {
            LOGGER.error("Bean '{}' definition failed", beanClass.getName(), e);
        }
    }

}