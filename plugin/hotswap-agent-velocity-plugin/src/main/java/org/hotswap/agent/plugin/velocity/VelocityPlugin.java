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
package org.hotswap.agent.plugin.velocity;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author raymond
 */
@Plugin(name = "Velocity",
        description = "Enhance org.springframework.ui.velocity.VelocityEngineFactory",
        testedVersions = {"4.3.8.RELEASE"},
        expectedVersions = {"4.3.8.RELEASE"}
)
public class VelocityPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(VelocityPlugin.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.ui.velocity.VelocityEngineFactory")
    public static void patchSetPreferFileSystemAccess(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("setPreferFileSystemAccess", new CtClass[]{classPool.get("boolean")});
            method.insertAfter("this.preferFileSystemAccess = false;");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch parseAndProcess method for {}", ctClass.getName(), e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.ui.velocity.VelocityEngineFactory")
    public static void patchSetResourceLoaderPath(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("setResourceLoaderPath", new CtClass[]{classPool.get("java.lang.String")});
            method.insertAfter("this.resourceLoaderPath = \"classpath:/$$ha$velocity/,\" + this.resourceLoaderPath;");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch parseAndProcess method for {}", ctClass.getName(), e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.ui.velocity.VelocityEngineFactory")
    public static void patchInitSpringResourceLoader(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("initSpringResourceLoader", new CtClass[]{classPool.get("org.apache.velocity.app.VelocityEngine"),
                    classPool.get("java.lang.String")});
            method.insertAfter("$1.setProperty(\"spring.resource.loader.cache\", \"false\");");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch parseAndProcess method for {}", ctClass.getName(), e);
        }
    }
}
