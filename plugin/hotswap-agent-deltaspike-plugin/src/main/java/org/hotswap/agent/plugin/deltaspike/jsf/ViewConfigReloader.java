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
                Class<?> viewConfigRootClass = classLoader.loadClass((String)oClass);
                if (viewConfigRootClass != null) {
                    doAddPageDefinition(viewConfigExtension, viewConfigRootClass);
                }
            }
            viewConfigExtension.buildViewConfig(null);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Deltaspike view config reloading failed", e);
        }
    }

    private static void doAddPageDefinition(ViewConfigExtension viewConfigExtension, Class<?> viewConfigClass) {
        if (ViewConfigUtils.isFolderConfig(viewConfigClass)) {
            viewConfigExtension.addFolderDefinition(viewConfigClass);
        } else if (ViewConfig.class.isAssignableFrom(viewConfigClass)){
            viewConfigExtension.addPageDefinition((Class<? extends ViewConfig>) viewConfigClass);
        }
        for (Class<?> subclass: viewConfigClass.getDeclaredClasses()) {
            doAddPageDefinition(viewConfigExtension, subclass);
        }
    }
}
