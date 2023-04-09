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
package org.hotswap.agent.plugin.jbossmodules;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
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
        testedVersions = {"1.4.4, 1.5.1, 1.6.x, 1.7.x, 1.8.x, 1.9.x, 1.10.x"},
        expectedVersions = {"1.x"},
        supportClass={ModuleClassLoaderTransformer.class}
)
public class JBossModulesPlugin {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(JBossModulesPlugin.class);

    // TODO : Skip system packages, it should be in config file
    private static final String SKIP_MODULES_REGEXP = "sun\\.jdk.*|ibm\\.jdk.*|javax\\..*|org\\.jboss\\..*";
    private static final String USE_MODULES_REGEXP = "deployment\\..*";

    @Init
    ClassLoader moduleClassLoader;

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("JBossModules plugin plugin initialized.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.modules.ModuleLoader")
    public static void transformModule(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        try {
            ctClass.getDeclaredMethod("loadModule", new CtClass[]{classPool.get(String.class.getName())}).insertAfter(
                    "if ($1.matches(\"" + USE_MODULES_REGEXP + "\")) {" +
                        PluginManagerInvoker.buildInitializePlugin(JBossModulesPlugin.class, "$_.getClassLoaderPrivate()") +
                    "}" +
                    "return $_;"
                );
            ctClass.getDeclaredMethod("unloadModuleLocal", new CtClass[]{classPool.get(String.class.getName()), classPool.get("org.jboss.modules.Module")}).insertBefore(
                        "if(!$1.matches(\"" + SKIP_MODULES_REGEXP + "\")) {" +
                            PluginManagerInvoker.buildCallCloseClassLoader("$2.getClassLoaderPrivate()") +
                        "}"
                    );
        } catch (NotFoundException e) {
            ctClass.getDeclaredMethod("loadModule", new CtClass[]{classPool.get("org.jboss.modules.ModuleIdentifier")}).insertAfter(
                        "if ($1.getName().matches(\"" + USE_MODULES_REGEXP + "\")) {" +
                            PluginManagerInvoker.buildInitializePlugin(JBossModulesPlugin.class, "$_.getClassLoaderPrivate()") +
                        "}" +
                        "return $_;"
                    );
            ctClass.getDeclaredMethod("unloadModuleLocal", new CtClass[]{classPool.get("org.jboss.modules.Module")}).insertBefore(
                        "if(!$1.getIdentifier().getName().matches(\"" + SKIP_MODULES_REGEXP + "\")) {" +
                            PluginManagerInvoker.buildCallCloseClassLoader("$1.getClassLoaderPrivate()") +
                        "}"
                    );

        }

        LOGGER.debug("Class 'org.jboss.modules.Module' patched.");
    }
}
