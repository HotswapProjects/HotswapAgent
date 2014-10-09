package org.hotswap.agent.plugin.jsf;

import java.lang.reflect.Method;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "JSF",
        description = "JSF Framework refreshes resource bundles. Bean cache reset is solved by ELResolved plugin",
        testedVersions = {"2.1.23, 2.2.8"},
        expectedVersions = {"2.1", "2.2"})
public class JsfPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JsfPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.config.ConfigManager")
    public static void facesServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("initialize");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class));
        LOGGER.debug("javax.faces.webapp.FacesServlet enhanced with plugin initialization.");
    }

    @OnResourceFileEvent(path = "/", filter = ".*messages_.*.properties")
    public void refreshJsfResourceBundles() {
        scheduler.scheduleCommand(refreshLabels);
    }

    private Command refreshLabels = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing JSF resource bundles cache.");
            try {
                Class<?> clazz = resolveClass("java.util.ResourceBundle");
                Method clearCacheMethod = clazz.getDeclaredMethod("clearCache", ClassLoader.class);
                clearCacheMethod.invoke(null, appClassLoader);
            } catch (Exception e) {
                LOGGER.error("Error clear JSF resource bundles cache", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
