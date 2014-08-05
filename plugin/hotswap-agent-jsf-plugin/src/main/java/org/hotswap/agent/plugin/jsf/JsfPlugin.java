package org.hotswap.agent.plugin.jsf;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "JSF",
        description = "JSF Framework is currently empty (no refresh needed). " +
                "Bean refresh is covered by ELResolverPlugin.",
        testedVersions = {"2.1.23"},
        expectedVersions = {"2.1", "2.2"})
public class JsfPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JsfPlugin.class);

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.config.ConfigManager")
    public static void facesServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("initialize");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class));
        LOGGER.debug("javax.faces.webapp.FacesServlet enhanced with plugin initialization.");
    }
}
