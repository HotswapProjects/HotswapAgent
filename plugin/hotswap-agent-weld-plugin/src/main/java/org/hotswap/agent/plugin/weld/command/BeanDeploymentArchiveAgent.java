package org.hotswap.agent.plugin.weld.command;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.logging.AgentLogger;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.backed.BackedAnnotatedType;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.resources.ReflectionCache;
import org.jboss.weld.resources.ReflectionCacheFactory;
import org.jboss.weld.resources.SharedObjectCache;

public class BeanDeploymentArchiveAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveAgent.class);

    private static Map<String, BeanDeploymentArchiveAgent> instances = new HashMap<String, BeanDeploymentArchiveAgent>();

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    WeldBeanDeploymentArchive deploymentArchive;

    // list of basePackages registered with target scanner
    String bdaId;

    private boolean registered = false;

    public static BeanDeploymentArchiveAgent registerArchive(WeldBeanDeploymentArchive beanArchive) {
        BeanDeploymentArchiveAgent bdaAgent = null;
        File path;
        String pathPrefix = beanArchive.getId();
        try {
            // check that it is regular file
            // toString() is weird and solves HiarchicalUriException for URI like "file:./src/resources/file.txt".
            path = new File(pathPrefix);
            if (!instances.containsKey(beanArchive)) {
                bdaAgent = new BeanDeploymentArchiveAgent(beanArchive);
                instances.put(beanArchive.getId(), bdaAgent);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to watch deployment archive with archive id=", pathPrefix);
        }

        return bdaAgent;
    }

    /**
     * Get archive instance by supplied path
     * @param bdaId the Bean Deployment Archive ID
     * @return the archive agent
     */
    public static BeanDeploymentArchiveAgent getInstance(String bdaId) {
        for (BeanDeploymentArchiveAgent scannerAgent : instances.values()) {
            if (bdaId.equals(scannerAgent.bdaId))
                return scannerAgent;
        }
        return null;
    }

    // Create new instance from getInstance(ClassPathBeanDefinitionScanner scanner) and obtain services from the scanner
    private BeanDeploymentArchiveAgent(WeldBeanDeploymentArchive archive) {
        this.deploymentArchive = archive;
        this.bdaId = archive.getId();
    }

    public void register() {
        if (!registered) {
            // TODO : there is a problem that WeldPlugin.class is unknown in applClassLoader
//            PluginManagerInvoker.callPluginMethod(WeldPlugin.class, getClass().getClassLoader(),
//                    "registerBeanDeplArchivePath", new Class[]{String.class}, new Object[]{deploymentArchivePath});
            registered = true;
        }
    }

    /**
     * Called by a reflection command from WeldPlugin transformer.
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName the bean class name
     * @throws IOException error working with classDefinition
     */
    public static void refreshClass(String bdaId, String beanClassName) throws IOException {
        BeanDeploymentArchiveAgent scannerAgent = getInstance(bdaId);
        if (scannerAgent == null) {
            LOGGER.error("basePackage '{}' not associated with any archiveAgent", bdaId);
            return;
        }
        scannerAgent.reloadBean(bdaId, beanClassName);

        reloadFlag = false;
    }

    /**
     * Reload bean in existing bean manager.
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName the bean class name
     */
    public void reloadBean(String bdaId, String beanClassName) {

        try {
            Class<?> beanClass = this.getClass().getClassLoader().loadClass(beanClassName);

            BeanManagerImpl beanManager = ((BeanManagerProxy) CDI.current().getBeanManager()).unwrap();

            Set<Bean<?>> beans = beanManager.getBeans(beanClass);

            if (beans != null && !beans.isEmpty()) {
                for (Bean<?> bean : beans) {
                    TypeStore store = new TypeStore();
                    SharedObjectCache cache = new SharedObjectCache();
                    ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
                    ClassTransformer classTransformer = new ClassTransformer(store,
                            cache, reflectionCache, "STATIC_INSTANCE");
                    BackedAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, beanClass, bdaId);

                    EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);

                    ((ManagedBean) bean).setProducer(
                            beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, bean, false)
                    );
                }
            }
            else
            {
                // TODO : create new bean
            }
        }
        catch (Exception e)
        {
            // TODO:
        }
    }

}
