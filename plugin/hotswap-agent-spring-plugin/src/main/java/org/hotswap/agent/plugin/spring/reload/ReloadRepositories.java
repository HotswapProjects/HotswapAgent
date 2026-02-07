package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

public class ReloadRepositories {

    private static Object repositoryConfigurationDelegate;
    private static Object registry;
    private static Object repositoryConfigurationExtension;

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadRepositories.class);

    public static void storeReferences(Object repositoryConfigurationDelegate, Object registry,
            Object repositoryConfigurationExtension) {
        ReloadRepositories.repositoryConfigurationDelegate = repositoryConfigurationDelegate;
        ReloadRepositories.registry = registry;
        ReloadRepositories.repositoryConfigurationExtension = repositoryConfigurationExtension;

    }

    public static void reloadAllRepositories() {
        if (repositoryConfigurationDelegate == null || registry == null || repositoryConfigurationExtension == null) {
            LOGGER.debug("Skipping repository reload because references are not initialized");
            return;
        }
        LOGGER.trace("Rescanning and reloading all JPA repositories");
        ReflectionHelper.invoke(repositoryConfigurationDelegate, repositoryConfigurationDelegate.getClass(),
                "registerRepositoriesIn",
                new Class[] { BeanDefinitionRegistry.class,
                        ScanNewEntities.findClass(
                                "org.springframework.data.repository.config.RepositoryConfigurationExtension") },
                registry,
                repositoryConfigurationExtension);
    }
}
