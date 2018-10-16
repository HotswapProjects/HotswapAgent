package org.hotswap.agent.plugin.deltaspike.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook PartialBeanBindingExtension to register DeltaSpikePlugin
 *
 * @author Vladimir Dvorak
 */
public class PartialBeanTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PartialBeanTransformer.class);

    /**
     * Patch partial bean binding extension.
     *
     * @param ctClass
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.partialbean.impl.PartialBeanBindingExtension")
    public static void patchPartialBeanBindingExtension(CtClass ctClass)  throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class));
        LOGGER.debug("org.apache.deltaspike.partialbean.impl.PartialBeanBindingExtension enhanced with plugin initialization.");

        CtMethod createPartialBeanMethod = ctClass.getDeclaredMethod("createPartialBean");
        createPartialBeanMethod.insertAfter(
            "if (" + PluginManager.class.getName() + ".getInstance().isPluginInitialized(\"" + DeltaSpikePlugin.class.getName() + "\", beanClass.getClassLoader())) {" +
                PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerPartialBean",
                        "$_", "java.lang.Object",
                        "beanClass", "java.lang.Class"
                        ) +
            "}" +
            "return $_;"
        );
    }

}
