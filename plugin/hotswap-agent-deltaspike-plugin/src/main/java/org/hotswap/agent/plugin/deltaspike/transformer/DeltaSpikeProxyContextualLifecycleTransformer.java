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
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Patch RepositoryMetadataHandlerTransformer
 *
 * @author Vladimir Dvorak
 */
public class DeltaSpikeProxyContextualLifecycleTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikeProxyContextualLifecycleTransformer.class);

    /**
     * Register DeltaspikePlugin and add hook to create method to DeltaSpikeProxyContextualLifecycle.
     *
     * @param classPool the class pool
     * @param ctClass   the ctclass
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException      the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.api.DeltaSpikeProxyContextualLifecycle")
    public static void patchDeltaSpikeProxyContextualLifecycle(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        CtMethod methodCreate = ctClass.getDeclaredMethod("create");
        methodCreate.insertAfter(
                "{" +
                    PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class) +
                    PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerRepoProxy", "$_", "java.lang.Object", "this.targetClass", "java.lang.Class")+
                "}" +
                "return $_;"
        );

        LOGGER.debug("org.apache.deltaspike.proxy.api.DeltaSpikeProxyContextualLifecycle - registration hook added.");
    }

}
