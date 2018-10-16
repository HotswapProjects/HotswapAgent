package org.hotswap.agent.plugin.deltaspike.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.NewExpr;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook ViewConfigExtension to initialize DeltaspikePlugin
 *
 * @author Vladimir Dvorak
 */
public class ViewConfigTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ViewConfigTransformer.class);

    private static final String VIEW_CONFIG_RESOLVER_PROXY_FIELD = "$$ha$viewConfigResolverProxy";

    /**
     * Register DeltaspikePlugin and add reinitialization method to RepositoryComponent
     *
     * @param ctClass
     * @param classPool the class pool
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension")
    public static void patchViewConfigExtension(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(
            "{" +
                "if (this.isActivated) {" +
                    PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class)+
                "}" +
            "}"
        );
        LOGGER.debug("org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension enhanced with plugin initialization.");

        CtClass viewConfigResProxyClass = classPool.get("org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverProxy");
        CtField viewConfigResProxyField = new CtField(viewConfigResProxyClass, VIEW_CONFIG_RESOLVER_PROXY_FIELD, ctClass);
        ctClass.addField(viewConfigResProxyField);

        CtMethod generateProxyClassMethod = ctClass.getDeclaredMethod("transformMetaDataTree");

        generateProxyClassMethod.instrument(
            new ExprEditor() {
                public void edit(NewExpr e) throws CannotCompileException {
                    if (e.getClassName().equals("org.apache.deltaspike.jsf.impl.config.view.DefaultViewConfigResolver"))
                    e.replace(
                        "{ " +
                            "java.lang.Object _resolver = new org.apache.deltaspike.jsf.impl.config.view.DefaultViewConfigResolver($$); " +
                            "if (this." + VIEW_CONFIG_RESOLVER_PROXY_FIELD + "==null) {" +
                                "this." + VIEW_CONFIG_RESOLVER_PROXY_FIELD + "=new org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverProxy();" +
                            "}" +
                            "this." + VIEW_CONFIG_RESOLVER_PROXY_FIELD + ".setViewConfigResolver(_resolver);" +
                            "java.util.List _list = org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverUtils.findViewConfigRootClasses(this.rootViewConfigNode);" +
                            PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerViewConfigRootClasses",
                                "this", "java.lang.Object", "_list", "java.util.List") +
                            "   $_ = this." + VIEW_CONFIG_RESOLVER_PROXY_FIELD + ";" +
                        "}"
                    );
                }
            }
        );
    }

}
