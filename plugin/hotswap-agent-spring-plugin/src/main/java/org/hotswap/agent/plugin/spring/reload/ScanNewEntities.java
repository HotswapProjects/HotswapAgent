package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

public class ScanNewEntities {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ScanNewEntities.class);
    private static Object persistenceManagedTypesScanner;
    private static Object packagesToScan;

    public static void storeScanner(Object persistenceManagedTypesScanner, Object packagesToScan) {
        ScanNewEntities.persistenceManagedTypesScanner = persistenceManagedTypesScanner;
        ScanNewEntities.packagesToScan = packagesToScan;
    }

    public static void scanNewEntities(Object localContainerEntityManagerFactoryBean) {
        if (persistenceManagedTypesScanner == null || packagesToScan == null) {
            LOGGER.debug("Skipping entity rescan because scanner references are not initialized");
            return;
        }
        if (localContainerEntityManagerFactoryBean == null) {
            LOGGER.debug("Skipping entity rescan because LocalContainerEntityManagerFactoryBean is null");
            return;
        }
        LOGGER.trace("Rescanning for entities");
        Object persistenceManagedTypes = ReflectionHelper.invoke(persistenceManagedTypesScanner,
                persistenceManagedTypesScanner.getClass(), "scan",
                new Class[] { String[].class }, packagesToScan);
        ReflectionHelper.invoke(localContainerEntityManagerFactoryBean,
                localContainerEntityManagerFactoryBean.getClass(), "setManagedTypes",
                new Class[] { findClass("org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes") },
                persistenceManagedTypes);

        ReflectionHelper.invoke(localContainerEntityManagerFactoryBean, "afterPropertiesSet");
    }

    static Class<?> findClass(String string) {
        try {
            return Class.forName(string);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + string, e);
        }
    }

}
