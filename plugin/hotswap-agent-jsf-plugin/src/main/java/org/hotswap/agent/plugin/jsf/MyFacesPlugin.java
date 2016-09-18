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
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "MyFaces",
        description = "JSF/MyFaces. Clear resource bundle cache when *.properties files are changed.",
        testedVersions = {"2.2.10"},
        expectedVersions = {"2.2"})
public class MyFacesPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MyFacesPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.config.RuntimeConfig")
    public static void facesApplicationAssociateInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String buildInitializePlugin = PluginManagerInvoker.buildInitializePlugin(MyFacesPlugin.class);

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(buildInitializePlugin);
        }
        LOGGER.debug("org.apache.myfaces.config.RuntimeConfig with plugin initialization.");
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
            } catch (Exception e) {
                LOGGER.error("Error clear JSF resource bundles cache", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
