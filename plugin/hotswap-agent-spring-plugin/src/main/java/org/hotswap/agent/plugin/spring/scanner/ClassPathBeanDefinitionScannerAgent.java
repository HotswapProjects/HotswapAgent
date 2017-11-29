package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.ResetBeanPostProcessorCaches;
import org.hotswap.agent.plugin.spring.ResetRequestMappingCaches;
import org.hotswap.agent.plugin.spring.ResetSpringStaticCaches;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registers
 *
 * @author Jiri Bubnik
 */
public class ClassPathBeanDefinitionScannerAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanDefinitionScannerAgent.class);

    private static Map<ClassPathBeanDefinitionScanner, ClassPathBeanDefinitionScannerAgent> instances = new HashMap<ClassPathBeanDefinitionScanner, ClassPathBeanDefinitionScannerAgent>();

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    // target scanner this agent shadows
    ClassPathBeanDefinitionScanner scanner;

    // list of basePackages registered with target scanner
    Set<String> basePackages = new HashSet<String>();

    // registry obtained from the scanner
    BeanDefinitionRegistry registry;

    // metadata resolver obtained from the scanner
    ScopeMetadataResolver scopeMetadataResolver;

    // bean name generator obtained from the scanner
    BeanNameGenerator beanNameGenerator;

    /**
     * Return an agent instance for a scanner. If the instance does not exists yet, it is created.
     * @param scanner the scanner
     * @return agent instance
     */
    public static ClassPathBeanDefinitionScannerAgent getInstance(ClassPathBeanDefinitionScanner scanner) {
        if (!instances.containsKey(scanner)) {
            instances.put(scanner, new ClassPathBeanDefinitionScannerAgent(scanner));
        }
        return instances.get(scanner);
    }

    /**
     * Find scanner agent by base package.
     *
     * @param basePackage the scanner agent or null if no such agent exists
     * @return the agent
     */
    public static ClassPathBeanDefinitionScannerAgent getInstance(String basePackage) {
        for (ClassPathBeanDefinitionScannerAgent scannerAgent : instances.values()) {
            if (scannerAgent.basePackages.contains(basePackage))
                return scannerAgent;
        }
        return null;
    }

    // Create new instance from getInstance(ClassPathBeanDefinitionScanner scanner) and obtain services from the scanner
    private ClassPathBeanDefinitionScannerAgent(ClassPathBeanDefinitionScanner scanner) {
        this.scanner = scanner;

        this.registry = scanner.getRegistry();
        this.scopeMetadataResolver = (ScopeMetadataResolver) ReflectionHelper.get(scanner, "scopeMetadataResolver");
        this.beanNameGenerator = (BeanNameGenerator) ReflectionHelper.get(scanner, "beanNameGenerator");
    }

    /**
     * Initialize base package from ClassPathBeanDefinitionScanner.scan() (hooked by a Transformer)
     * @param basePackage package that Spring will scan
     */
    public void registerBasePackage(String basePackage) {
        this.basePackages.add(basePackage);

        PluginManagerInvoker.callPluginMethod(SpringPlugin.class, getClass().getClassLoader(),
                "registerComponentScanBasePackage", new Class[]{String.class}, new Object[]{basePackage});
    }

    /**
     * Called by a reflection command from SpringPlugin transformer.
     *
     * @param basePackage base package on witch the transformer was registered, used to obtain associated scanner.
     * @param classDefinition new class definition
     * @throws IOException error working with classDefinition
     */
    public static void refreshClass(String basePackage, byte[] classDefinition) throws IOException {
        ClassPathBeanDefinitionScannerAgent scannerAgent = getInstance(basePackage);
        if (scannerAgent == null) {
            LOGGER.error("basePackage '{}' not associated with any scannerAgent", basePackage);
            return;
        }

        BeanDefinition beanDefinition = scannerAgent.resolveBeanDefinition(classDefinition);
        scannerAgent.defineBean(beanDefinition);

        reloadFlag = false;
    }


    /**
     * Resolve candidate to a bean definition and (re)load in Spring.
     * Synchronize to avoid parallel bean definition - usually on reload the beans are interrelated
     * and parallel load will cause concurrent modification exception.
     *
     * @param candidate the candidate to reload
     */
    public void defineBean(BeanDefinition candidate) {
        synchronized (getClass()) { // TODO sychronize on DefaultListableFactory.beanDefinitionMap?
            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
            candidate.setScope(scopeMetadata.getScopeName());
            String beanName = this.beanNameGenerator.generateBeanName(candidate, registry);
            if (candidate instanceof AbstractBeanDefinition) {
                postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
            }
            if (candidate instanceof AnnotatedBeanDefinition) {
                processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
            }

            clearCahceIfExists(beanName);

            DefaultListableBeanFactory bf = maybeRegistryToBeanFactory();

            // use previous singleton bean, if modified class is not bean, a exception will be throw
            Object bean;
            try {
                bean = bf.getBean(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                LOGGER.warning("{} is not managed by spring", beanName);
                return;
            }
            BeanWrapper bw = new BeanWrapperImpl(bf.getBean(beanName));
            RootBeanDefinition rootBeanDefinition = (RootBeanDefinition)ReflectionHelper.invoke(bf, AbstractBeanFactory.class,
                    "getMergedLocalBeanDefinition", new Class[]{String.class},
                    beanName);
            ReflectionHelper.invoke(bf, AbstractAutowireCapableBeanFactory.class,
                    "populateBean", new Class[]{String.class, RootBeanDefinition.class, BeanWrapper.class},
                    beanName,rootBeanDefinition , bw);

            freezeConfiguration();


			ProxyReplacer.clearAllProxies();
        }

    }

    /**
     * If registry contains the bean, remove it first (destroying existing singletons).
     * @param beanName name of the bean
     */
    private void clearCahceIfExists(String beanName) {
        if (registry.containsBeanDefinition(beanName)) {
            ResetSpringStaticCaches.reset();
            DefaultListableBeanFactory bf = maybeRegistryToBeanFactory();
            if (bf != null) {
            	ResetBeanPostProcessorCaches.reset(bf);
                ResetRequestMappingCaches.reset(bf);
            }
        }
    }
    
    private DefaultListableBeanFactory maybeRegistryToBeanFactory() {
        if (registry instanceof DefaultListableBeanFactory) {
            return (DefaultListableBeanFactory)registry;
        } else if (registry instanceof GenericApplicationContext) {
            return ((GenericApplicationContext) registry).getDefaultListableBeanFactory();
        }
        return null;
    }

    // rerun freez configuration - this method is enhanced with cache reset
    private void freezeConfiguration() {
        if (registry instanceof DefaultListableBeanFactory) {
            ((DefaultListableBeanFactory)registry).freezeConfiguration();
        } else if (registry instanceof GenericApplicationContext) {
            (((GenericApplicationContext) registry).getDefaultListableBeanFactory()).freezeConfiguration();
        }
    }

    /**
     * Resolve bean definition from class definition if applicable.
     *
     * @param bytes class definition.
     * @return the definition or null if not a spring bean
     * @throws IOException
     */
    public BeanDefinition resolveBeanDefinition(byte[] bytes) throws IOException {
        Resource resource = new ByteArrayResource(bytes);
        resetCachingMetadataReaderFactoryCache();
        MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
        ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
        sbd.setResource(resource);
        sbd.setSource(resource);
        return sbd;
    }

    private MetadataReaderFactory getMetadataReaderFactory() {
        return (MetadataReaderFactory) ReflectionHelper.get(scanner, "metadataReaderFactory");
    }

    // metadataReader contains cache of loaded classes, reset this cache before BeanDefinition is resolved
    private void resetCachingMetadataReaderFactoryCache() {
        if (getMetadataReaderFactory() instanceof CachingMetadataReaderFactory) {
            Map metadataReaderCache = (Map) ReflectionHelper.getNoException(getMetadataReaderFactory(),
                        CachingMetadataReaderFactory.class, "metadataReaderCache");

            if (metadataReaderCache == null)
                metadataReaderCache = (Map) ReflectionHelper.getNoException(getMetadataReaderFactory(),
                        CachingMetadataReaderFactory.class, "classReaderCache");

            if (metadataReaderCache != null) {
                metadataReaderCache.clear();
                LOGGER.debug("Cache cleared: CachingMetadataReaderFactory.clearCache()");
            } else {
                LOGGER.warning("Cache NOT cleared: neither CachingMetadataReaderFactory.metadataReaderCache nor clearCache does not exist.");
            }


        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////
    // Access private / protected members
    ////////////////////////////////////////////////////////////////////////////////////////////

    private BeanDefinitionHolder applyScopedProxyMode(
            ScopeMetadata metadata, BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
        return (BeanDefinitionHolder) ReflectionHelper.invoke(null, AnnotationConfigUtils.class,
                "applyScopedProxyMode", new Class[]{ScopeMetadata.class, BeanDefinitionHolder.class, BeanDefinitionRegistry.class},
                metadata, definition, registry);

    }

    private void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
        ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "registerBeanDefinition", new Class[]{BeanDefinitionHolder.class, BeanDefinitionRegistry.class}, definitionHolder, registry);
    }

    private boolean checkCandidate(String beanName, BeanDefinition candidate) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "checkCandidate", new Class[]{String.class, BeanDefinition.class}, beanName, candidate);
    }

    private void processCommonDefinitionAnnotations(AnnotatedBeanDefinition candidate) {
        ReflectionHelper.invoke(null, AnnotationConfigUtils.class,
                "processCommonDefinitionAnnotations", new Class[]{AnnotatedBeanDefinition.class}, candidate);
    }

    private void postProcessBeanDefinition(AbstractBeanDefinition candidate, String beanName) {
        ReflectionHelper.invoke(scanner, ClassPathBeanDefinitionScanner.class,
                "postProcessBeanDefinition", new Class[]{AbstractBeanDefinition.class, String.class},
                candidate, beanName);
    }

    private boolean isCandidateComponent(AnnotatedBeanDefinition sbd) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathScanningCandidateComponentProvider.class,
                "isCandidateComponent", new Class[]{AnnotatedBeanDefinition.class}, sbd);
    }

    private boolean isCandidateComponent(MetadataReader metadataReader) {
        return (Boolean) ReflectionHelper.invoke(scanner, ClassPathScanningCandidateComponentProvider.class,
                "isCandidateComponent", new Class[]{MetadataReader.class}, metadataReader);
    }
}