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
 * Patch RepositoryMetadataHandlerTransformer
 *
 * @author Vladimir Dvorak
 */
public class RepositoryMetadataHandlerTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepositoryMetadataHandlerTransformer.class);

    public static final String REINITIALIZE_METHOD = "$$ha$reinitialize";

    /**
     * Register DeltaspikePlugin and add reinitialization method to RepositoryComponent
     *
     * @param ctClass
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.data.impl.meta.RepositoryMetadataHandler")
    public static void patchRepositoryComponent(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerRepositoryMetadataHandler", "this", "java.lang.Object"));
        src.append("}");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        ctClass.addMethod(CtNewMethod.make("public void " + REINITIALIZE_METHOD + "(java.lang.Class repositoryClass) {" +
        			"org.apache.deltaspike.data.impl.meta.RepositoryMetadata metadata = metadataInitializer.init(repositoryClass, this.beanManager);" +
        			"this.repositoriesMetadata.put(repositoryClass, metadata);" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.data.impl.meta.RepositoryMetadataHandler - registration hook and reinitialization method added.");
    }

}