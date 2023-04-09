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
package org.hotswap.agent.plugin.omnifaces;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Omnifaces (http://omnifaces.org/)
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Omnifaces",
        description = "Omnifaces (http://omnifaces.org//), support for view scope reinjection/reloading",
        testedVersions = {"2.6.8"},
        expectedVersions = {"2.6"}
)
public class OmnifacesPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OmnifacesPlugin.class);

    private boolean initialized;

    public void init() {
        if (!initialized) {
            LOGGER.info("Omnifaces plugin initialized.");
            initialized = true;
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.ApplicationListener")
    public static void patchApplicationListener(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.getDeclaredMethod("contextInitialized").insertAfter(
            "{" +
                PluginManagerInvoker.buildInitializePlugin(OmnifacesPlugin.class) +
                PluginManagerInvoker.buildCallPluginMethod(OmnifacesPlugin.class, "init") +
            "}"
        );
    }

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.cdi.viewscope.ViewScopeContext")
    public static void patchViewScopeContext(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        HaCdiCommons.transformContext(classPool, ctClass);
    }

}

