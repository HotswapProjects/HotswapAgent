package org.hotswap.agent.plugin.deltaspike.jsf;

import java.util.List;

import org.apache.deltaspike.core.api.config.view.ViewConfig;
import org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension;
import org.apache.deltaspike.jsf.impl.util.ViewConfigUtils;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class ViewConfigReloader.
 */
public class ViewConfigReloader {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ViewConfigReloader.class);

    public static void reloadViewConfig(ClassLoader classLoader, Object viewConfigExtensionObj, List rootClassNameList) {
        try {
            ViewConfigExtension viewConfigExtension = (ViewConfigExtension) viewConfigExtensionObj;
            viewConfigExtension.freeViewConfigCache(null);
            ReflectionHelper.invoke(viewConfigExtension, viewConfigExtension.getClass(), "resetRootNode", null);
            for (Object oClass : rootClassNameList) {
                Class<?> viewConfigRootClass = Class.forName((String)oClass, true, classLoader);
                if (viewConfigRootClass != null) {
                    doAddPageDefinition(classLoader, viewConfigExtension, viewConfigRootClass);
                }
            }
            viewConfigExtension.buildViewConfig(null);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Deltaspike view config reloading failed", e);
        }
    }

    private static void doAddPageDefinition(ClassLoader classLoader, ViewConfigExtension viewConfigExtension, Class<?> viewConfigClass) {
        if (ViewConfigUtils.isFolderConfig(viewConfigClass)) {
            viewConfigExtension.addFolderDefinition(viewConfigClass);
        } else if (ViewConfig.class.isAssignableFrom(viewConfigClass)){
            viewConfigExtension.addPageDefinition((Class<? extends ViewConfig>) viewConfigClass);
        }
        for (Class<?> subClass: viewConfigClass.getDeclaredClasses()) {
            Class<?> reloadedSubclass;
            try {
                reloadedSubclass = Class.forName(subClass.getName(), true, classLoader);
                if (reloadedSubclass != null) {
                    doAddPageDefinition(classLoader, viewConfigExtension, reloadedSubclass);
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("ViewConfig subclass removed {} ", subClass.getName());
            }
        }
    }
}
