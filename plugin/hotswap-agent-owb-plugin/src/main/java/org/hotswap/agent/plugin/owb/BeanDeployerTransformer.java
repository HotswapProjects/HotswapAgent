package org.hotswap.agent.plugin.owb;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into org.apache.webbeans.config.BeansDeployer deploy to initialize OwbPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeanDeployerTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeployerTransformer.class);

    /**
     * Basic CdiArchive transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.config.BeansDeployer")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        StringBuilder src = new StringBuilder(" if (deployed) {");
        src.append("ClassLoader curCl = Thread.currentThread().getContextClassLoader();");
        src.append(PluginManagerInvoker.buildInitializePlugin(OwbPlugin.class, "curCl"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("curCl", OwbPlugin.class, "init"));
        src.append("}");

        CtMethod startApplication = clazz.getDeclaredMethod("deploy");
        startApplication.insertAfter(src.toString());

        LOGGER.debug("Class '{}' patched with OwbPlugin registration.", clazz.getName());
    }

}
