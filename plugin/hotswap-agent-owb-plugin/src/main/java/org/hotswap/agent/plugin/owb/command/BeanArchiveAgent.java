package org.hotswap.agent.plugin.owb.command;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
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
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.web.context.WebContextsService;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.plugin.owb.BeanReloadStrategy;
import org.hotswap.agent.plugin.owb.OwbClassSignatureHelper;
import org.hotswap.agent.plugin.owb.OwbPlugin;
import org.hotswap.agent.plugin.owb.WebBeansContextsServiceTransformer;
import org.hotswap.agent.plugin.owb.beans.ContextualReloadHelper;
import org.hotswap.agent.plugin.owb.command.WebContextsTracker.WebContextsSet;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Handles creating and redefinition of bean classes in BeanArchive
 *
 * @author Vladimir Dvorak
 */
public class BeanArchiveAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanArchiveAgent.class);

    /** True for UnitTests */
    public static boolean isTestEnvironment = false;

    /**
     * Flag to check the reload status. In unit test we need to wait for reload
     * finishing before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    private ClassLoader archiveClassLoader;

    private Archive beanArchive;

    private String archivePath;

    private boolean registered = false;

    /**
     * Register archive into ArchiveAgentRegistry.
     *
     * @param archiveClassLoader the archive class loader
     * @param archive the archive
     */
    public static void registerArchive(ClassLoader archiveClassLoader, Archive archive) {

        try {
            String archivePath = null;

            if (archive instanceof FileArchive) {
                archivePath = ((FileArchive) archive).getDir().getPath();
            } else {
                LOGGER.warning("Unsupported Archive class={}", archive.getClass().getName());
            }

            if (archivePath != null) {

                if (!ArchiveAgentRegistry.contains(archivePath)) {
                    BeanArchiveAgent beanArchiveAgent = new BeanArchiveAgent(archive, archiveClassLoader, archivePath);
                    ArchiveAgentRegistry.put(archivePath, beanArchiveAgent);
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
            LOGGER.debug("Archive {} registered.", archivePath);
            PluginManagerInvoker.callPluginMethod(OwbPlugin.class, getClass().getClassLoader(),
                    "registerBeanArchivePath", new Class[] { ClassLoader.class, String.class }, new Object[] { archiveClassLoader, archivePath });
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

    private BeanArchiveAgent(Archive beanArchive, ClassLoader archiveClassLoader, String archivePath) {
        this.beanArchive = beanArchive;
        this.archiveClassLoader = archiveClassLoader;
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
     * @param appClassLoader the application class loader
     * @param archivePath the archive path
     * @param beanClassName the bean class name
     * @param oldSignatureByStrategy the old signature by strategy
     * @param strReloadStrategy the bean reload strategy
     * @throws IOException error working with classDefinition
     */
    public static void reloadBean(ClassLoader appClassLoader, String archivePath, String beanClassName, String oldSignatureByStrategy,
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

            Class<?> beanClass = archiveAgent.archiveClassLoader.loadClass(beanClassName);

            archiveAgent.doReloadBean(appClassLoader, beanClass, oldSignatureByStrategy, reloadStrategy);

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
    private void doReloadBean(ClassLoader appClassLoader, Class<?> beanClass, String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

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
                        if (bean instanceof ManagedBean) {
                            doReloadManagedBean(beanManager, beanClass, (ManagedBean) bean, oldSignatureByStrategy, reloadStrategy);
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
    private void doReloadManagedBean(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean,
            String oldSignatureByStrategy, BeanReloadStrategy reloadStrategy) {

        createAnnotatedTypeForExistingBeanClass(beanManager, beanClass, managedBean);

        String signatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(reloadStrategy, beanClass);

        if (reloadStrategy == BeanReloadStrategy.CLASS_CHANGE ||
                (reloadStrategy != BeanReloadStrategy.NEVER && signatureByStrategy != null && !signatureByStrategy.equals(oldSignatureByStrategy))) {

            // Reload bean in contexts - invalidates existing instances
            doReloadManagedBeanInContexts(beanManager, beanClass, managedBean);

        } else {

            // keep beans in contexts, reinitialize bean injection points
            try {
                WebBeansContext wbc = beanManager.getWebBeansContext();
                ContextsService contextsService = wbc.getContextsService();

                if (!isTestEnvironment && contextsService instanceof WebContextsService) {
                    // For WebContextService(web application) iterate over all combination of context
                    WebContextsTracker contextTracker =
                            (WebContextsTracker) ReflectionHelper.get(contextsService, WebBeansContextsServiceTransformer.CONTEXT_TRACKER_FLD_NAME);
                    if (contextTracker != null) {
                        try {
                            for (WebContextsSet wcs: contextTracker.getWebContextsSetList()) {
                                contextTracker.setWebContextsSet(wcs);
                                Object get = beanManager.getContext(managedBean.getScope()).get(managedBean);
                                if (get != null) {
                                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                                    managedBean.getProducer().inject(get, beanManager.createCreationalContext(managedBean));
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
                    Object get = beanManager.getContext(managedBean.getScope()).get(managedBean);
                    if (get != null) {
                        LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
                        managedBean.getProducer().inject(get, beanManager.createCreationalContext(managedBean));
                    }
                }
            } catch (javax.enterprise.context.ContextNotActiveException e) {
                LOGGER.warning("No active contexts for {}", beanClass.getName());
            }

        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void createAnnotatedTypeForExistingBeanClass(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        clearAnnotElemFactoryCaches(beanManager);

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches
        annotatedElementFactory.clear();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);

        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "annotatedType", annotatedType);

        // Updated members that were set by bean attributes
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "types", attributes.getTypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "qualifiers", attributes.getQualifiers());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "scope", attributes.getScope());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "name", attributes.getName());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "stereotypes", attributes.getStereotypes());
        ReflectionHelper.set(managedBean, BeanAttributesImpl.class, "alternative", attributes.isAlternative());

        InjectionTargetFactory factory = new InjectionTargetFactoryImpl(annotatedType, managedBean.getWebBeansContext());
        InjectionTarget injectionTarget = factory.createInjectionTarget(managedBean);
        ReflectionHelper.set(managedBean, InjectionTargetBean.class, "injectionTarget", injectionTarget);

        LOGGER.debug("New annotated type created for beanClass {}", beanClass.getName());
    }

    private void clearAnnotElemFactoryCaches(BeanManagerImpl beanManager) {

        AnnotatedElementFactory annoElementFactory = beanManager.getWebBeansContext().getAnnotatedElementFactory();

        clearMapCache(annoElementFactory, "annotatedTypeCache");
        clearMapCache(annoElementFactory, "modifiedAnnotatedTypeCache");
        clearMapCache(annoElementFactory, "annotatedConstructorCache");
        clearMapCache(annoElementFactory, "annotatedMethodCache");
        clearMapCache(annoElementFactory, "annotatedFieldCache");
        clearMapCache(annoElementFactory, "annotatedMethodsOfTypeCache");

        LOGGER.trace("AnnotatedElementFactory cache cleared.");
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

    @SuppressWarnings("rawtypes")
    private void doReloadManagedBeanInContexts(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean) {
        try {
            Map<Class<? extends Annotation>, List<Context>> allContexts = getContexts(beanManager);

            List<Context> ctxList = allContexts.get(managedBean.getScope());

            if(ctxList != null) {
                for(Context context: ctxList) {
                    if (context != null) {
                        LOGGER.debug("Inspecting context '{}' for bean class {}", context.getClass(), managedBean.getScope());
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

    /**
     * Recreate proxy classes, Called from BeanClassRefreshCommand.
     *
     * @param appClassLoader the application class loader
     * @param beanClassName the bean class name
     * @param oldSignatureForProxyCheck the old signature for proxy check
     * @throws IOException error working with classDefinition
     */

    public static void recreateProxy(ClassLoader appClassLoader, String archivePath, String beanClassName, String oldSignatureForProxyCheck) throws IOException {

        BeanArchiveAgent archiveAgent = ArchiveAgentRegistry.get(archivePath);
        if (archiveAgent == null) {
            LOGGER.error("Archive path '{}' is not associated with any BeanArchiveAgent", archivePath);
            return;
        }

        try {
            Class<?> beanClass = archiveAgent.archiveClassLoader.loadClass(beanClassName);
            archiveAgent.doRecreateProxy(appClassLoader, beanClass, oldSignatureForProxyCheck);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Bean class not found.", e);
        }
    }

    private void doRecreateProxy(ClassLoader appClassLoader, Class<?> beanClass, String oldClassSignature) {
        if (oldClassSignature != null) {
            String newClassSignature = OwbClassSignatureHelper.getSignatureForProxyClass(beanClass);
            if (newClassSignature != null && !newClassSignature.equals(oldClassSignature)) {
                doRecreateProxy(appClassLoader, beanClass);
            }
        }
    }

    private void doRecreateProxy(ClassLoader appClassLoader, Class<?> beanClass) {

        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            ProxyClassLoadingDelegate.beginProxyRegeneration();
            Thread.currentThread().setContextClassLoader(appClassLoader);

            WebBeansContext wbc = WebBeansContext.currentInstance();
            NormalScopeProxyFactory proxyFactory = wbc.getNormalScopeProxyFactory();

            // Clear proxy class cache
            Map cachedProxyClasses = (Map) ReflectionHelper.get(proxyFactory, "cachedProxyClasses");
            Set<Bean<?>> beans = wbc.getBeanManagerImpl().getBeans(beanClass);
            if (beans != null) {
                boolean recreateIt = false;
                for (Bean<?> bean : beans) {
                    if (cachedProxyClasses.containsKey(bean)) {
                        cachedProxyClasses.remove(bean);
                        recreateIt = true;
                    }
                    if (recreateIt) {
                        proxyFactory.createProxyClass(appClassLoader, beanClass);
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
}
