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
package org.hotswap.agent.plugin.spring.processor;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class ConfigurationClassPostProcessorTransformer {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ConfigurationClassPostProcessorTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.annotation.ConfigurationClassPostProcessor")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod method = clazz.getDeclaredMethod("processConfigBeanDefinitions",
                new CtClass[]{classPool.get("org.springframework.beans.factory.support.BeanDefinitionRegistry")});
        method.insertAfter("org.hotswap.agent.plugin.spring.processor.ConfigurationClassPostProcessorAgent.getInstance()." +
                "setProcessor(this);");
        LOGGER.debug("Class 'org.springframework.context.annotation.ConfigurationClassPostProcessor' patched with processor registration.");
    }
}
