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
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
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
     * @param classPool the class pool
     * @param ctClass   the ct class
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.partialbean.impl.PartialBeanBindingExtension")
    public static void patchPartialBeanBindingExtension(ClassPool classPool, CtClass ctClass)  throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
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
        LOGGER.debug("org.apache.deltaspike.partialbean.impl.PartialBeanBindingExtension patched.");
    }

}
