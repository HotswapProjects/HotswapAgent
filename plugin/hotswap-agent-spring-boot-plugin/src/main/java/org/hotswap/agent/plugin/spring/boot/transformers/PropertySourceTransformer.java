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
package org.hotswap.agent.plugin.spring.boot.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class PropertySourceTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertySourceTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.core.env.MapPropertySource")
    public static void transformMapPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        transformPropertySource(clazz, classPool);
        LOGGER.debug("Patch org.springframework.boot.env.MapPropertySource success");
    }

    @OnClassLoadEvent(classNameRegexp= "org.springframework.boot.env.OriginTrackedMapPropertySource")
    public static void transformOriginTrackedMapPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        transformPropertySource(clazz, classPool);
        LOGGER.debug("Patch org.springframework.boot.env.OriginTrackedMapPropertySource success");
    }

    private static void transformPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.ReloadablePropertySource"));
        clazz.addField(CtField.make("private org.hotswap.agent.plugin.spring.api.PropertySourceReloader reload;", clazz));

        clazz.addMethod(CtMethod.make("public void setReload(org.hotswap.agent.plugin.spring.api.PropertySourceReloader r) { this.reload = r; }", clazz));
        clazz.addMethod(CtMethod.make("public void reload() { if (this.reload != null) {this.reload.reload();} }", clazz));
    }
}
