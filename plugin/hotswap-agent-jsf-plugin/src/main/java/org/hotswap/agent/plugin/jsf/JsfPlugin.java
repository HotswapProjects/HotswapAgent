package org.hotswap.agent.plugin.jsf;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "JSF",
        description = "JSF/Mojarra. Clear resource bundle cache when *.properties files are changed.",
        testedVersions = {"2.1.23, 2.2.8"},
        expectedVersions = {"2.1", "2.2"})
public class JsfPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JsfPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredRBMaps = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.config.ConfigManager")
    public static void facesConfigManagerInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("initialize");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class));
        LOGGER.debug("com.sun.faces.config.ConfigManager enhanced with plugin initialization.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.application.ApplicationResourceBundle")
    public static void facesApplicationAssociateInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String registerResourceBundle = PluginManagerInvoker.buildCallPluginMethod(JsfPlugin.class, "registerApplicationResourceBundle",
                "baseName", "java.lang.String", "resources", "java.lang.Object");
        String buildInitializePlugin = PluginManagerInvoker.buildInitializePlugin(JsfPlugin.class);

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(buildInitializePlugin);
            constructor.insertAfter(registerResourceBundle);
        }
        LOGGER.debug("com.sun.faces.application.ApplicationAssociate enhanced with resource bundles registration.");
    }

    public void registerApplicationResourceBundle(String baseName, Object resourceBundle) {
        registeredRBMaps.add(resourceBundle);
        LOGGER.debug("JsfPlugin - resource bundle '" + baseName + "' registered");
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properties")
    public void refreshJsfResourceBundles() {
        scheduler.scheduleCommand(refreshResourceBundles);
    }

    private Command refreshResourceBundles = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing JSF resource bundles.");
            try {
                Class<?> clazz = resolveClass("java.util.ResourceBundle");
                Method clearCacheMethod = clazz.getDeclaredMethod("clearCache", ClassLoader.class);
                clearCacheMethod.invoke(null, appClassLoader);
                for (Object resourceMap : registeredRBMaps) {
                    if (resourceMap instanceof Map) {
                        ((Map) resourceMap).clear();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error clear JSF resource bundles cache", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
