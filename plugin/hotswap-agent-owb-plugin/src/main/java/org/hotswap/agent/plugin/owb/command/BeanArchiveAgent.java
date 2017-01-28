package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.InjectionTargetFactory;
import javax.enterprise.inject.spi.ObserverMethod;

import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.component.CdiInterceptorBean;
import org.apache.webbeans.component.DecoratorBean;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.component.ProducerFieldBean;
import org.apache.webbeans.component.ProducerMethodBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.component.creation.CdiInterceptorBeanBuilder;
import org.apache.webbeans.component.creation.DecoratorBeanBuilder;
import org.apache.webbeans.component.creation.ManagedBeanBuilder;
import org.apache.webbeans.component.creation.ObserverMethodsBuilder;
import org.apache.webbeans.component.creation.ProducerFieldBeansBuilder;
import org.apache.webbeans.component.creation.ProducerMethodBeansBuilder;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.container.InjectionTargetFactoryImpl;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.decorator.DecoratorsManager;
import org.apache.webbeans.event.ObserverMethodImpl;
import org.apache.webbeans.intercept.InterceptorsManager;
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.portable.events.ProcessBeanImpl;
import org.apache.webbeans.portable.events.generics.GProcessManagedBean;
import org.apache.webbeans.util.WebBeansUtil;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.CompositeArchive;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.FilteredArchive;
import org.apache.xbean.finder.archive.JarArchive;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.plugin.owb.BeanReloadStrategy;
import org.hotswap.agent.plugin.owb.OwbClassSignatureHelper;
import org.hotswap.agent.plugin.owb.OwbPlugin;
import org.hotswap.agent.plugin.owb.beans.ContextualReloadHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Handles creating and redefinition of bean classes in BeanArchive
 *
 * @author Vladimir Dvorak
 */
public class BeanArchiveAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanArchiveAgent.class);

    /**
     * Flag to check the reload status. In unit test we need to wait for reload
     * finishing before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    private Archive beanArchive;

    private String archivePath;

    private boolean registered = false;

    /**
     * Register CDI bean archive into ArchiveAgentRegistry.
     *
     * @param classLoader the class loader
     * @param beanArchive the bean archive to be registered
     */
    @SuppressWarnings("unchecked")
    public static void registerCdiArchiveDelegate(ClassLoader classLoader, CdiArchive beanArchive) {

        try {
            CompositeArchive compositeArchive = (CompositeArchive) ReflectionHelper.get(beanArchive, "delegate");
            List<Archive> archives = (List<Archive>) ReflectionHelper.get(compositeArchive, "archives");
            for (Archive archive: archives) {
                String archivePath = null;
                if (archive instanceof FilteredArchive) {
                    archive = (Archive) ReflectionHelper.get(archive, "archive");
                }
                if (archive instanceof JarArchive) {
                    archivePath = ((JarArchive) archive) .getUrl().getPath();
                } else if (archive instanceof FileArchive) {
                    archivePath = ((FileArchive) archive).getDir().getPath();
                } else {
                    LOGGER.warning("Unexpected Archive class={}", archive.getClass().getName());
                }
                boolean contain = (boolean) ReflectionHelper.invoke(null, Class.forName(ArchiveAgentRegistry.class.getName(), true, classLoader), "contains", new Class[] {String.class}, archivePath);
                if (!contain) {
                    BeanArchiveAgent beanArchiveAgent = new BeanArchiveAgent(archive, archivePath);
                    ReflectionHelper.invoke(null, Class.forName(ArchiveAgentRegistry.class.getName(), true, classLoader),
                        "put", new Class[] {String.class, BeanArchiveAgent.class}, archivePath, beanArchiveAgent);
                    beanArchiveAgent.register();
                }
            }
        } catch (Exception e) {
            LOGGER.error("registerArchive() exception {}.", e.getMessage());
        }

    }

    private void register() {
        if (!registered) {
            registered = true;
            PluginManagerInvoker.callPluginMethod(OwbPlugin.class, getClass().getClassLoader(),
                    "registerBeanArchivePath", new Class[] { String.class }, new Object[] { archivePath });
        }
    }

    /**
     * Gets the collection of registered BeanArchive(s)
     *
     * @return the instances
     */
    public static Collection<BeanArchiveAgent> getInstances() {
        return ArchiveAgentRegistry.values();
    }

    private BeanArchiveAgent(Archive beanArchive, String archivePath) {
        this.beanArchive = beanArchive;
        this.archivePath = archivePath;
    }

    /**
     * Gets the archive path.
     *
     * @return the archive path
     */
    public String getArchivePath() {
        return archivePath;
    }

    public Archive getBeanArchive() {
        return beanArchive;
    }

    /**
     * Called by a reflection command from BeanRefreshCommand transformer.
     *
     * @param classLoader the class loader
     * @param archivePath the archive path
     * @param beanClassName the bean class name
     * @param oldSignatureByStrategy the old signature by strategy
     * @param strReloadStrategy the bean reload strategy
     * @throws IOException error working with classDefinition
     */
    public static void reloadBean(ClassLoader classLoader, String archivePath, String beanClassName, String oldSignatureByStrategy,
            String strReloadStrategy) throws IOException {

        BeanArchiveAgent archiveAgent = ArchiveAgentRegistry.get(archivePath);
        if (archiveAgent == null) {
            LOGGER.error("Archive path '{}' is not associated with any BeanArchiveAgent", archivePath);
            return;
        }

        try {

            BeanReloadStrategy reloadStrategy;

            try {
                reloadStrategy = BeanReloadStrategy.valueOf(strReloadStrategy);
            } catch (Exception e) {
                reloadStrategy = BeanReloadStrategy.NEVER;
            }

            // archive classLoader can be different then appClassLoader for EAR deployment
            // therefore we use class loader from archiveAgent class which is classloader for archive
            Class<?> beanClass = archiveAgent.getClass().getClassLoader().loadClass(beanClassName);

            archiveAgent.doReloadBean(classLoader, beanClass, oldSignatureByStrategy, reloadStrategy);

        } catch (ClassNotFoundException e) {
            LOGGER.error("Bean class not found.", e);
        } finally {
            reloadFlag = false;
        }
    }

    /**
     * Reload bean in existing bean manager.
     *
     * @param classLoader the class loader
     * @param beanClass the bean class
     * @param oldSignatureByStrategy the old signature by strategy
     * @param reloadStrategy the reload strategy
     */
    @SuppressWarnings("rawtypes")
    private void doReloadBean(ClassLoader classLoader, Class<?> beanClass, String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);

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
                        if (bean instanceof ManagedBean) {
                            clearAnnotElemFactoryCaches(beanManager);
                            doReloadManagedBean(beanManager, beanClass, (ManagedBean) bean, oldSignatureByStrategy, reloadStrategy);
                        } else {
                            LOGGER.warning("reloadBean() : class '{}' reloading is not implemented ({}).",
                                    bean.getClass().getName(), bean.getBeanClass());
                        }
                    }
                    LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
                } else {
                    // Create new bean
                    doDefineManagedBean(beanManager, beanClass);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    private void clearAnnotElemFactoryCaches(BeanManagerImpl beanManager) {

        AnnotatedElementFactory annoElementFactory = beanManager.getWebBeansContext().getAnnotatedElementFactory();

        clearMapCache(annoElementFactory, "annotatedTypeCache");
        clearMapCache(annoElementFactory, "modifiedAnnotatedTypeCache");
        clearMapCache(annoElementFactory, "annotatedConstructorCache");
        clearMapCache(annoElementFactory, "annotatedMethodCache");
        clearMapCache(annoElementFactory, "annotatedFieldCache");
        clearMapCache(annoElementFactory, "annotatedMethodsOfTypeCache");
    }

    private void clearMapCache(Object target, String fieldName) {
        try {
            Map m = (Map) ReflectionHelper.get(target, fieldName);
            if (m != null) {
                m.clear();
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Cannot clear cache AnnotatedElementFactory cache.", e);;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void doReloadManagedBean(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches
        annotatedElementFactory.clear();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);

        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "annotatedType", annotatedType);

        InjectionTargetFactory factory = new InjectionTargetFactoryImpl(annotatedType, managedBean.getWebBeansContext());
        InjectionTarget injectionTarget = factory.createInjectionTarget(managedBean);
        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "injectionTarget", injectionTarget);

        // Updated members that were set by bean attributes
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "types", attributes.getTypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "qualifiers", attributes.getQualifiers());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "scope", attributes.getScope());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "name", attributes.getName());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "stereotypes", attributes.getStereotypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "alternative", attributes.isAlternative());

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {

            // Reload bean in contexts - invalidates existing instances
            doReloadManagedBeanInContexts(beanManager, beanClass, managedBean);

        } else {

            // keep beans in contexts, reinitialize bean injection points
            try {
                Object get = beanManager.getContext(managedBean.getScope()).get(managedBean);
                if (get != null) {
                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                    managedBean.getProducer().inject(get, beanManager.createCreationalContext(managedBean));
                }
            } catch (javax.enterprise.context.ContextNotActiveException e) {
                LOGGER.
                warning("No active contexts for {}", beanClass.getName());
            }

        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void doDefineManagedBean(BeanManagerImpl beanManager, Class<?> beanClass) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches (is it necessary for definition ?)
        annotatedElementFactory.clear();

        // Injection resolver cache must be cleared before / after definition
        beanManager.getInjectionResolver().clearCaches();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();

        if(wbc.getWebBeansUtil().supportsJavaEeComponentInjections(beanClass)) {
            //Fires ProcessInjectionTarget
            wbc.getWebBeansUtil().fireProcessInjectionTargetEventForJavaEeComponents(beanClass).setStarted();
            wbc.getWebBeansUtil().inspectDeploymentErrorStack(
                    "There are errors that are added by ProcessInjectionTarget event observers. Look at logs for further details");
            //Checks that not contains @Inject InjectionPoint
            wbc.getAnnotationManager().checkInjectionPointForInjectInjectionPoint(beanClass);
        }

        {
            ManagedBeanBuilder managedBeanCreator = new ManagedBeanBuilder(wbc, annotatedType, attributes);
            DecoratorsManager decoratorsManager = wbc.getDecoratorsManager();
            InterceptorsManager interceptorsManager = wbc.getInterceptorsManager();

            if(WebBeansUtil.isDecorator(annotatedType)) {

                LOGGER.debug("Found Managed Bean Decorator with class name : [{}]", annotatedType.getJavaClass().getName());

                DecoratorBeanBuilder dbb = new DecoratorBeanBuilder(wbc, annotatedType, attributes);
                if (dbb.isDecoratorEnabled()) {
                    dbb.defineDecoratorRules();
                    DecoratorBean decorator = dbb.getBean();
                    decoratorsManager.addDecorator(decorator);
                }

            } else if(WebBeansUtil.isCdiInterceptor(annotatedType)) {
                LOGGER.debug("Found Managed Bean Interceptor with class name : [{}]", annotatedType.getJavaClass().getName());

                CdiInterceptorBeanBuilder ibb = new CdiInterceptorBeanBuilder(wbc, annotatedType, attributes);

                if (ibb.isInterceptorEnabled()) {
                    ibb.defineCdiInterceptorRules();
                    CdiInterceptorBean interceptor = (CdiInterceptorBean) ibb.getBean();
                    interceptorsManager.addCdiInterceptor(interceptor);
                }
            } else {
                InjectionTargetBean bean = managedBeanCreator.getBean();

                if (decoratorsManager.containsCustomDecoratorClass(annotatedType.getJavaClass()) ||
                        interceptorsManager.containsCustomInterceptorClass(annotatedType.getJavaClass())) {
                    return; //TODO discuss this case (it was ignored before)
                }

                LOGGER.debug("Found Managed Bean with class name : [{}]", annotatedType.getJavaClass().getName());

                Set<ObserverMethod<?>> observerMethods;
                AnnotatedType beanAnnotatedType = bean.getAnnotatedType();
//                AnnotatedType defaultAt = webBeansContext.getAnnotatedElementFactory().getAnnotatedType(beanAnnotatedType.getJavaClass());
                boolean ignoreProducer = false /*defaultAt != beanAnnotatedType && annotatedTypes.containsKey(defaultAt)*/;

                if(bean.isEnabled()) {
                    observerMethods = new ObserverMethodsBuilder(wbc, beanAnnotatedType).defineObserverMethods(bean);
                } else {
                    observerMethods = new HashSet<ObserverMethod<?>>();
                }

                Set<ProducerFieldBean<?>> producerFields =
                        ignoreProducer ? Collections.emptySet() : new ProducerFieldBeansBuilder(wbc, beanAnnotatedType).defineProducerFields(bean);
                Set<ProducerMethodBean<?>> producerMethods =
                        ignoreProducer ? Collections.emptySet() : new ProducerMethodBeansBuilder(wbc, beanAnnotatedType).defineProducerMethods(bean, producerFields);

                ManagedBean managedBean = (ManagedBean)bean;
                Map<ProducerMethodBean<?>,AnnotatedMethod<?>> annotatedMethods =
                        new HashMap<ProducerMethodBean<?>, AnnotatedMethod<?>>();

                if (!producerFields.isEmpty() || !producerMethods.isEmpty()) {
                    final Priority priority = annotatedType.getAnnotation(Priority.class);
                    if (priority != null && !wbc.getAlternativesManager()
                            .isAlternative(annotatedType.getJavaClass(), Collections.<Class<? extends Annotation>>emptySet())) {
                        wbc.getAlternativesManager().addPriorityClazzAlternative(annotatedType.getJavaClass(), priority);
                    }
                }

                for(ProducerMethodBean<?> producerMethod : producerMethods) {
                    AnnotatedMethod method = wbc.getAnnotatedElementFactory().newAnnotatedMethod(producerMethod.getCreatorMethod(), annotatedType);
                    wbc.getWebBeansUtil().inspectDeploymentErrorStack("There are errors that are added by ProcessProducer event observers for "
                            + "ProducerMethods. Look at logs for further details");

                    annotatedMethods.put(producerMethod, method);
                }

                Map<ProducerFieldBean<?>,AnnotatedField<?>> annotatedFields =
                        new HashMap<ProducerFieldBean<?>, AnnotatedField<?>>();

                for(ProducerFieldBean<?> producerField : producerFields) {
                    /* TODO: check if needed in HA
                    webBeansContext.getWebBeansUtil().inspectDeploymentErrorStack("There are errors that are added by ProcessProducer event observers for"
                            + " ProducerFields. Look at logs for further details");
                    */

                    annotatedFields.put(producerField,
                            wbc.getAnnotatedElementFactory().newAnnotatedField(
                                    producerField.getCreatorField(),
                                    wbc.getAnnotatedElementFactory().newAnnotatedType(producerField.getBeanClass())));
                }

                Map<ObserverMethod<?>,AnnotatedMethod<?>> observerMethodsMap =
                        new HashMap<ObserverMethod<?>, AnnotatedMethod<?>>();

                for(ObserverMethod<?> observerMethod : observerMethods) {
                    ObserverMethodImpl<?> impl = (ObserverMethodImpl<?>)observerMethod;
                    AnnotatedMethod<?> method = impl.getObserverMethod();

                    observerMethodsMap.put(observerMethod, method);
                }

                //Fires ProcessManagedBean
                ProcessBeanImpl processBeanEvent = new GProcessManagedBean(managedBean, annotatedType);
                beanManager.fireEvent(processBeanEvent, true);
                processBeanEvent.setStarted();
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessManagedBean event observers for " +
                        "managed beans. Look at logs for further details");

                //Fires ProcessProducerMethod
                wbc.getWebBeansUtil().fireProcessProducerMethodBeanEvent(annotatedMethods, annotatedType);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessProducerMethod event observers for " +
                        "producer method beans. Look at logs for further details");

                //Fires ProcessProducerField
                wbc.getWebBeansUtil().fireProcessProducerFieldBeanEvent(annotatedFields);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessProducerField event observers for " +
                        "producer field beans. Look at logs for further details");

                //Fire ObservableMethods
                wbc.getWebBeansUtil().fireProcessObservableMethodBeanEvent(observerMethodsMap);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessObserverMethod event observers for " +
                        "observer methods. Look at logs for further details");
                if(!wbc.getWebBeansUtil().isAnnotatedTypeDecoratorOrInterceptor(annotatedType)) {
                    beanManager.addBean(bean);
                    for (ProducerMethodBean<?> producerMethod : producerMethods) {
                        // add them one after the other to enable serialization handling et al
                        beanManager.addBean(producerMethod);
                    }
                    for (ProducerFieldBean<?> producerField : producerFields) {
                        // add them one after the other to enable serialization handling et al
                        beanManager.addBean(producerField);
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void doReloadManagedBeanInContexts(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> allContexts = getContexts(beanManager);

            List<Context> ctxList = allContexts.get(managedBean.getScope());

            if(ctxList != null) {
                for(Context context: ctxList) {
                    if (context != null) {
                        LOGGER.debug("Inspecting context..... '{}' {}", context.getClass(), managedBean.getScope());
                        if(ContextualReloadHelper.addToReloadSet(context, managedBean)) {
                            LOGGER.debug("Bean {}, added to reload set in context {}", managedBean, context.getClass());
                        } else {
                            // try to reinitialize injection points instead...
                            try {
                                Object get = context.get(managedBean);
                                if (get != null) {
                                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                                    managedBean.getProducer().inject(get, beanManager.createCreationalContext(managedBean));
                                }
                            } catch (Exception e) {
                                if(LOGGER.isLevelEnabled(Level.DEBUG)) {
                                    LOGGER.debug("Context {} not active for bean: {} in scope: {}",e, context.getClass(), beanClass.getName(), managedBean.getScope());
                                } else {
                                    LOGGER.warning("Context {} not active for bean: {} in scope: {}", context.getClass(), beanClass.getName(), managedBean.getScope());
                                }
                            }
                        }
                    } else {
                        LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
                    }
                }
            } else {
                LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
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

    /*
    private void refreshProxy(ClassLoader classLoader, Map<Object, Object> registeredProxiedBeans, Class<?> beanClass, String oldClassSignature) {
        if (oldClassSignature != null && registeredProxiedBeans != null) {
            String newClassSignature = OwbClassSignatureHelper.getSignatureForProxyClass(beanClass);
            if (newClassSignature != null && !newClassSignature.equals(oldClassSignature)) {
                synchronized (registeredProxiedBeans) {
                    if (!registeredProxiedBeans.isEmpty()) {
                        doRefreshProxy(classLoader, registeredProxiedBeans, beanClass);
                    }
                }
            }
        }
    }

    private void doRefreshProxy(ClassLoader classLoader, Map<Object, Object> registeredBeans, Class<?> proxyClass) {
        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        ProxyClassLoadingDelegate.beginProxyRegeneration();

        try {
            Class<?> proxyFactoryClass = null;

            for (Entry<Object, Object> entry : registeredBeans.entrySet()) {
                Bean<?> bean = (Bean<?>) entry.getKey();

                if (bean != null) {
                    Set<Type> types = bean.getTypes();
                    if (types.contains(proxyClass)) {
                        Thread.currentThread().setContextClassLoader(bean.getBeanClass().getClassLoader());
                        if (proxyFactoryClass == null) {
                            proxyFactoryClass = classLoader.loadClass("org.jboss.weld.bean.proxy.ProxyFactory");
                        }
                        Object proxyFactory = entry.getValue();
                        LOGGER.info("Recreate proxyClass {} for bean class {}.", proxyClass.getName(), bean.getClass());
                        ReflectionHelper.invoke(proxyFactory, proxyFactoryClass, "getProxyClass", new Class[] {});
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("recreateProxyFactory() exception {}.", e, e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            ProxyClassLoadingDelegate.endProxyRegeneration();
        }
    }
    */

}
