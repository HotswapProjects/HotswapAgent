package org.hotswap.agent.plugin.omnifaces;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Omnifaces (http://omnifaces.org/)
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Omnifaces",
        description = "Omnifaces (http://omnifaces.org//), support for view scope reinjection/reloading",
        testedVersions = {"2.6.8"},
        expectedVersions = {"2.6"},
        supportClass = { OmnifacesTransformer.class }
)
public class OmnifacesPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OmnifacesPlugin.class);

    private boolean initialized;

    public void init() {
        if (!initialized) {
            LOGGER.info("OmnifacesPlugin plugin initialized.");
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.ApplicationListener")
    public static void patchApplicationListener(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.getDeclaredMethod("contextInitialized").insertAfter(
            "{" +
                PluginManagerInvoker.buildInitializePlugin(OmnifacesPlugin.class) +
                PluginManagerInvoker.buildCallPluginMethod(OmnifacesPlugin.class, "init") +
            "}"
        );
    }
}

