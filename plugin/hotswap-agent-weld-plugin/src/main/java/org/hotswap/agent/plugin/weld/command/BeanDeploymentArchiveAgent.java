package org.hotswap.agent.plugin.weld.command;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.backed.BackedAnnotatedType;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.attributes.BeanAttributesFactory;
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.resources.ReflectionCache;
import org.jboss.weld.resources.ReflectionCacheFactory;
import org.jboss.weld.resources.SharedObjectCache;

public class BeanDeploymentArchiveAgent {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveAgent.class);

    /**
     * Flag to check reload status. In unit test we need to wait for reload
     * finish before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    BeanDeploymentArchive deploymentArchive;

    String archivePath;

    boolean registered = false;

    public static void registerArchive(BeanDeploymentArchive beanArchive) {
       registerArchive(beanArchive, beanArchive.getId());
    }

    public static void registerArchive(BeanDeploymentArchive beanArchive, String archivePath) {
        BeanDeploymentArchiveAgent bdaAgent = null;
        String bdaId = beanArchive.getId();
        try {
            // check that it is regular file
            // toString() is weird and solves HiearchicalUriException for URI like "file:./src/resources/file.txt".
            File path = new File(archivePath);
            bdaAgent = BdaAgentRegistry.get(bdaId);
            if (bdaAgent == null) {
                bdaAgent = new BeanDeploymentArchiveAgent(beanArchive, archivePath);
                BdaAgentRegistry.put(bdaId, bdaAgent);
            }
            bdaAgent.register();
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to watch deployment archive with archive id=", bdaId);
        }


    }

    /**
     * Get archive instance by supplied bdaId
     *
     * @param bdaId the Bean Deployment Archive ID
     * @return the archive agent
     */
    public static BeanDeploymentArchiveAgent getInstance(String bdaId) {
        return BdaAgentRegistry.getByBdaIdFromValues(bdaId);
    }

    /**
     * Gets the collection of registered BeanDeploymentArchive(s)
     *
     * @return the instances
     */
    public static Collection<BeanDeploymentArchiveAgent> getInstances() {
        return BdaAgentRegistry.values();
    }

    private BeanDeploymentArchiveAgent(BeanDeploymentArchive deploymentArchive, String archivePath) {
        this.deploymentArchive = deploymentArchive;
        this.archivePath = archivePath;
    }

    /**
     * Gets the bdaId.
     *
     * @return the bdaId
     */
    public String getBdaId(){
        return deploymentArchive.getId();
    }

    /**
     * Gets the archive path.
     *
     * @return the archive path
     */
    public String getArchivePath() {
        return archivePath;
    }

    private void register() {
        if (!registered) {
            registered = true;
            PluginManagerInvoker.callPluginMethod(WeldPlugin.class, getClass().getClassLoader(),
                    "registerBeanDeplArchivePath", new Class[]{String.class, String.class}, new Object[]{getBdaId(), archivePath});
        }
    }

    /**
     * Called by a reflection command from WeldPlugin transformer.
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName
     * @throws IOException error working with classDefinition
     */
    public static void refreshClass(String bdaId, String beanClassName) throws IOException {
        BeanDeploymentArchiveAgent bdaAgent = getInstance(bdaId);
        if (bdaAgent == null) {
            LOGGER.error("basePackage '{}' not associated with any archiveAgent", bdaId);
            return;
        }
        bdaAgent.reloadBean(bdaId, beanClassName);

        reloadFlag = false;
    }

    /**
     * Reload bean in existing bean manager.
     *
     * @param bdaId the Bean Deployment Archive ID
     * @param beanClassName
     */
    public void reloadBean(String bdaId, String beanClassName) {

        try {
            Class<?> beanClass = this.getClass().getClassLoader().loadClass(beanClassName);
            // check if it is Object descendant
            if (Object.class.isAssignableFrom(beanClass)) {
                BeanManagerImpl beanManager = ((BeanManagerProxy) CDI.current().getBeanManager()).unwrap();

                Set<Bean<?>> beans = beanManager.getBeans(beanClass);

                if (beans != null && !beans.isEmpty()) {
                    for (Bean<?> bean : beans) {
                        EnhancedAnnotatedType eat = getEnhancedAnnotatedType(bdaId, beanClass);
                        final ManagedBean managedBean = (ManagedBean) bean;

                        managedBean.setProducer(
                                beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, bean, false)
                        );
                        try {
                            Object get = beanManager.getContext(bean.getScope()).get(bean);
                            if (get != null) {
                                LOGGER.debug("Bean injections point reinitialize '{}'", beanClassName);
                                managedBean.getProducer().inject(get, beanManager.createCreationalContext(bean));
                            }
                        } catch (org.jboss.weld.context.ContextNotActiveException e) {
                            LOGGER.warning("No active contexts for {}", beanClass.getName());
                        }
                    }
                    LOGGER.debug("Bean reloaded '{}'", beanClassName);
                } else {
                    try {
                        EnhancedAnnotatedType eat = getEnhancedAnnotatedType(bdaId, beanClass);
                        BeanAttributes attributes = BeanAttributesFactory.forBean(eat, beanManager);
                        ManagedBean<?> bean = ManagedBean.of(attributes, eat, beanManager);
                        Field field = beanManager.getClass().getDeclaredField("beanSet");
                        field.setAccessible(true);
                        field.set(beanManager, Collections.synchronizedSet(new HashSet<Bean<?>>()));
                        // TODO:
                        beanManager.addBean(bean);
                        beanManager.getBeanResolver().clear();
//                        beanManager.cleanupAfterBoot();
                        LOGGER.debug("Bean defined '{}'", beanClassName);
                    } catch (Exception ex) {
                        LOGGER.debug(bdaId, ex);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.warning("Class load exception : {}", e.getMessage());
        }
    }

    private EnhancedAnnotatedType getEnhancedAnnotatedType(String bdaId, Class<?> beanClass) {
        TypeStore store = new TypeStore();
        SharedObjectCache cache = new SharedObjectCache();
        ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
        ClassTransformer classTransformer = new ClassTransformer(store, cache, reflectionCache, "STATIC_INSTANCE");
        BackedAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, beanClass, bdaId);

        EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
        return eat;
    }

}
