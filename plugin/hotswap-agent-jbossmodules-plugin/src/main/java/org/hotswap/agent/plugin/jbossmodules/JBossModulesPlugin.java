package org.hotswap.agent.plugin.jbossmodules;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * JBossModulesPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "JBossModules",
        description = "JBossModules - Jboss modular class loading implementation. ",
        testedVersions = {"1.4.4"},
        expectedVersions = {"1.x"},
        supportClass={ModuleClassLoaderTransformer.class}
)
public class JBossModulesPlugin {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(JBossModulesPlugin.class);

    // TODO : Skip system packages, it should be in config file
    private static final String SKIP_MODULES_REGEXP = "sun\\.jdk.*|ibm\\.jdk.*|javax\\..*|org\\.jboss\\..*";

    @Init
    ClassLoader moduleClassLoader;

    @OnClassLoadEvent(classNameRegexp = "org.jboss.modules.ModuleLoader")
    public static void transformModule(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        ctClass.getDeclaredMethod("loadModule", new CtClass[]{classPool.get("org.jboss.modules.ModuleIdentifier")}).insertAfter(
                    "if(!identifier.getName().matches(\"" + SKIP_MODULES_REGEXP + "\")) {" +
                        PluginManagerInvoker.buildInitializePlugin(JBossModulesPlugin.class, "$_.getClassLoaderPrivate()") +
                    "}" +
                    "return $_;"
                );

        ctClass.getDeclaredMethod("unloadModuleLocal", new CtClass[]{classPool.get("org.jboss.modules.Module")}).insertBefore(
                    "if(!$1.getIdentifier().getName().matches(\"" + SKIP_MODULES_REGEXP + "\")) {" +
                        PluginManagerInvoker.buildCallCloseClassLoader("$1.getClassLoaderPrivate()") +
                    "}"
                );

        LOGGER.debug("Class 'org.jboss.modules.Module' patched.");
    }
}
