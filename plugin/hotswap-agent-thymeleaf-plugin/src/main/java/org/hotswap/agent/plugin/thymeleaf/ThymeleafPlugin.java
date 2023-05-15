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
package org.hotswap.agent.plugin.thymeleaf;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

@Plugin(name = "Thymeleaf",
        description = "Clear cache from TemplateManager when template is modified.",
        testedVersions = {"3.0.15"},
        expectedVersions = {"3.0.15"}
)
public class ThymeleafPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ThymeleafPlugin.class);

    @OnClassLoadEvent(classNameRegexp = "org.thymeleaf.engine.TemplateManager")
    public static void patchParseAndProcess(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("parseAndProcess", new CtClass[]{
                    classPool.get("org.thymeleaf.TemplateSpec"), classPool.get("org.thymeleaf.context.IContext"),
                    classPool.get("java.io.Writer")});
            method.insertBefore("clearCachesFor($1.getTemplate());");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch parseAndProcess method for {}", ctClass.getName(), e);
        }
    }
}