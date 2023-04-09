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
package org.hotswap.agent.plugin.undertow;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * The Class UndertowTransformer - patch DeploymentManagerImpl
 */
public class UndertowTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(UndertowTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "io.undertow.servlet.core.DeploymentManagerImpl")
    public static void patchWebappLoader(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        try {
            ctClass.getDeclaredMethod("deploy").insertBefore( "{" +
                    "org.hotswap.agent.plugin.undertow.PrefixingResourceManager rm=" +
                        "new org.hotswap.agent.plugin.undertow.PrefixingResourceManager(originalDeployment.getResourceManager());" +
                    "originalDeployment.setResourceManager(rm);" +
                    UndertowPlugin.class.getName() + ".init(originalDeployment.getClassLoader(),rm);" +
                "}"
            );
        } catch (NotFoundException e) {
            LOGGER.error("io.undertow.servlet.core.DeploymentManagerImpl does not contain start() method.");
        }

        try {
            ctClass.getDeclaredMethod("stop").insertBefore(
                    PluginManagerInvoker.buildCallCloseClassLoader("originalDeployment.getClassLoader()") +
                    UndertowPlugin.class.getName() + ".close(originalDeployment.getClassLoader());"
            );
        } catch (NotFoundException e) {
            LOGGER.error("orgio.undertow.servlet.core.DeploymentManagerImpl does not contain stop() method.");
        }

    }
}
