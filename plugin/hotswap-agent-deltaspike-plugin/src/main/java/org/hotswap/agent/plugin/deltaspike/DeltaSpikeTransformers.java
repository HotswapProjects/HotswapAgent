package org.hotswap.agent.plugin.deltaspike;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.javassist.expr.NewExpr;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook PartialBeanBindingExtension to register DeltaSpikePlugin
 * Hook into DeltaSpikeProxyFactory generator to register proxy factory into DeltaSpikePlugin.
 *
 * @author Vladimir Dvorak
 */
public class DeltaSpikeTransformers {

    private static final String VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME = "__viewConfigResolverProxy";
    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikeTransformers.class);

    /**
     * Patch partial bean binding extension.
     *
     * @param ctClass the ct class
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

    /**
     * Delegates ClassUtils.tryToLoadClassForName to org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate::tryToLoadClassForName
     *
     * @param ctClass the ct class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.api.DeltaSpikeProxyFactory")
    public static void patchDeltaSpikeProxyFactory(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod getProxyClassMethod = ctClass.getDeclaredMethod("getProxyClass");
        getProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.apache.deltaspike.core.util.ClassUtils") && m.getMethodName().equals("tryToLoadClassForName"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate.tryToLoadClassForName($$); }");
                    }
                });
        CtMethod createProxyClassMethod = ctClass.getDeclaredMethod("createProxyClass");
        createProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.apache.deltaspike.core.util.ClassUtils") && m.getMethodName().equals("tryToLoadClassForName"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate.tryToLoadClassForName($$); }");
                    }
                });
    }

    /**
     * Delegates loadClass to org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate::loadClass
     *
     * @param ctClass the ct class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.impl.AsmProxyClassGenerator")
    public static void patchAsmProxyClassGenerator(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtMethod generateProxyClassMethod = ctClass.getDeclaredMethod("generateProxyClass");
        generateProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.apache.deltaspike.proxy.impl.AsmProxyClassGenerator") && m.getMethodName().equals("loadClass"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate.loadClass($$); }");
                    }
                });
    }

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

        ctClass.addMethod(CtNewMethod.make("public void __reinitialize() {" +
                "   this.methods.clear(); " +
                "   initialize();" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.data.impl.meta.RepositoryComponent - added registering hook + method __reinitialize().");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension")
    public static void patchViewConfigExtension(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        StringBuilder src = new StringBuilder("{");
        src.append("    if (this.isActivated) {");
        src.append(         PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class));
        src.append("    }");
        src.append("}");
        init.insertAfter(src.toString());
        LOGGER.debug("org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension enhanced with plugin initialization.");

        CtClass viewConfigResProxyClass = classPool.get("org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverProxy");
        CtField viewConfigResProxyField = new CtField(viewConfigResProxyClass, VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME, ctClass);
        ctClass.addField(viewConfigResProxyField);

        CtMethod generateProxyClassMethod = ctClass.getDeclaredMethod("transformMetaDataTree");

        generateProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(NewExpr e) throws CannotCompileException {
                        if (e.getClassName().equals("org.apache.deltaspike.jsf.impl.config.view.DefaultViewConfigResolver"))
                        e.replace("{ " +
                                "   java.lang.Object _resolver = new org.apache.deltaspike.jsf.impl.config.view.DefaultViewConfigResolver($$); " +
                                "   if (this." + VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME + "==null) {" +
                                "       this." + VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME + "=new org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverProxy();" +
                                "   }" +
                                "   this." + VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME + ".setViewConfigResolver(_resolver);" +
                                "   java.util.List _list = org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigResolverUtils.findViewConfigRootClasses(this.rootViewConfigNode);" +
                                    PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerViewConfigRootClasses",
                                        "this", "java.lang.Object", "_list", "java.util.List") +
                                "   $_ = this." + VIEW_CONFIG_RESOLVER_PROXY_FLD_NAME + ";" +
                        "}");
                    }
                });
    }

}