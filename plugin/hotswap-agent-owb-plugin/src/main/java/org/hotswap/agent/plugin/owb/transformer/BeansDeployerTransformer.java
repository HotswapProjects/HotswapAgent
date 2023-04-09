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
package org.hotswap.agent.plugin.owb.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.owb.OwbPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into org.apache.webbeans.config.BeansDeployer deploy to initialize OwbPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeansDeployerTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeansDeployerTransformer.class);

    /**
     * Basic CdiArchive transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.config.BeansDeployer")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        StringBuilder src = new StringBuilder(" if (deployed) {");
        src.append("ClassLoader curCl = Thread.currentThread().getContextClassLoader();");
        src.append(PluginManagerInvoker.buildInitializePlugin(OwbPlugin.class, "curCl"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("curCl", OwbPlugin.class, "init"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("curCl", OwbPlugin.class, "registerBeansXmls", "$1.getBeanXmls()", "java.util.Set"));
        src.append("}");

        CtMethod startApplication = clazz.getDeclaredMethod("deploy");
        startApplication.insertAfter(src.toString());

        LOGGER.debug("Class '{}' patched with OwbPlugin registration.", clazz.getName());
    }

}
