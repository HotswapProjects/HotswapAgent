/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
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
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
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
     * @param classPool the class pool
     * @param ctClass   the ct class
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException      the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension")
    public static void patchViewConfigExtension(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
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
