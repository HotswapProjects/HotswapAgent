package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
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
import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.container.InjectionTargetFactoryImpl;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.portable.AnnotatedElementFactory;
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

                BeanManagerImpl beanManagerImpl = null;
                BeanManager bm = CDI.current().getBeanManager();

                if (bm instanceof BeanManagerImpl) {
                    beanManagerImpl = (BeanManagerImpl) bm;
                } else if (bm instanceof InjectableBeanManager){
                    beanManagerImpl = (BeanManagerImpl) ReflectionHelper.get(bm, "bm");
                }

                Set<Bean<?>> beans = beanManagerImpl.getBeans(beanClass);

                if (beans != null && !beans.isEmpty()) {
                    for (Bean<?> bean : beans) {
                        if (bean instanceof ManagedBean) {
                            clearCaches(beanManagerImpl);
                            doReloadManagedBean(beanManagerImpl, beanClass, (ManagedBean) bean, oldSignatureByStrategy, reloadStrategy);
                        } else {
                            LOGGER.warning("reloadBean() : class '{}' reloading is not implemented ({}).",
                                    bean.getClass().getName(), bean.getBeanClass());
                        }
                    }
                    LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
                } else {
                    /*
                    try {
                        ClassTransformer classTransformer = getClassTransformer();
                        SlimAnnotatedType annotatedType = getAnnotatedType(getBdaId(), classTransformer, beanClass);
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
                    */
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    private void clearCaches(BeanManagerImpl beanManagerImpl) {

        AnnotatedElementFactory annoElementFactory = beanManagerImpl.getWebBeansContext().getAnnotatedElementFactory();

        clearAnnoElemFactoryCache(annoElementFactory, "annotatedTypeCache");
        clearAnnoElemFactoryCache(annoElementFactory, "modifiedAnnotatedTypeCache");
        clearAnnoElemFactoryCache(annoElementFactory, "annotatedConstructorCache");
        clearAnnoElemFactoryCache(annoElementFactory, "annotatedMethodCache");
        clearAnnoElemFactoryCache(annoElementFactory, "annotatedFieldCache");
        clearAnnoElemFactoryCache(annoElementFactory, "annotatedMethodsOfTypeCache");
    }

    private void clearAnnoElemFactoryCache(Object target, String fieldName) {
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
    private void doReloadManagedBean(BeanManagerImpl beanManagerImpl, Class<?> beanClass, ManagedBean managedBean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        WebBeansContext webBeansContext = beanManagerImpl.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = webBeansContext.getAnnotatedElementFactory();
        annotatedElementFactory.clear();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);

        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "annotatedType", annotatedType);

        InjectionTargetFactory factory = new InjectionTargetFactoryImpl(annotatedType, managedBean.getWebBeansContext());
        InjectionTarget injectionTarget = factory.createInjectionTarget(managedBean);
        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "injectionTarget", injectionTarget);

        BeanAttributesImpl beanAttributes = BeanAttributesBuilder.forContext(webBeansContext).newBeanAttibutes(annotatedType).build();

        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "types", beanAttributes.getTypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "qualifiers", beanAttributes.getQualifiers());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "scope", beanAttributes.getScope());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "name", beanAttributes.getName());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "stereotypes", beanAttributes.getStereotypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "alternative", beanAttributes.isAlternative());

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {

            // Reload bean in contexts - invalidates existing instances
            doReloadManagedBeanInContexts(beanManagerImpl, beanClass, managedBean);

        } else {

            // keep beans in contexts, reinitialize bean injection points
            try {
                Object get = beanManagerImpl.getContext(managedBean.getScope()).get(managedBean);
                if (get != null) {
                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                    managedBean.getProducer().inject(get, beanManagerImpl.createCreationalContext(managedBean));
                }
            } catch (javax.enterprise.context.ContextNotActiveException e) {
                LOGGER.warning("No active contexts for {}", beanClass.getName());
            }

        }
    }

    @SuppressWarnings("rawtypes")
    private void doReloadManagedBeanInContexts(BeanManagerImpl beanManagerImpl, Class<?> beanClass, ManagedBean managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> allContexts = getContexts(beanManagerImpl);

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
                                    managedBean.getProducer().inject(get, beanManagerImpl.createCreationalContext(managedBean));
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
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void defineManagedBean(BeanManagerImpl beanManager, EnhancedAnnotatedType eat) throws Exception {
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

    private ClassTransformer getClassTransformer() {
        TypeStore store = new TypeStore();
        SharedObjectCache cache = new SharedObjectCache();
        ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
        ClassTransformer classTransformer = new ClassTransformer(store, cache, reflectionCache, "STATIC_INSTANCE");
        return classTransformer;
    }
    @SuppressWarnings("rawtypes")
    private SlimAnnotatedType getAnnotatedType(String bdaId, ClassTransformer classTransformer, Class<?> beanClass) {
        BackedAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, bdaId);
        return annotatedType;
    }

*/
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
