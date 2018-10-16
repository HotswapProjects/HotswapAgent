package org.hotswap.agent.plugin.deltaspike.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook and patch RepositoryComponent
 *
 * @author Vladimir Dvorak
 */
public class RepositoryTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepositoryTransformer.class);

    public static final String REINITIALIZE_METHOD = "$$ha$reinitialize";

    /**
     * Register DeltaspikePlugin and add reinitialization method to RepositoryComponent
     *
     * @param ctClass
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.data.impl.meta.RepositoryComponent")
    public static void patchRepositoryComponent(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerRepoComponent",
                "this", "java.lang.Object",
                "this.repoClass", "java.lang.Class"));
        src.append("}");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        ctClass.addMethod(CtNewMethod.make("public void " + REINITIALIZE_METHOD + "() {" +
                "   this.methods.clear(); " +
                "   initialize();" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.data.impl.meta.RepositoryComponent - registration hook and reinitialization method added.");
    }

}
