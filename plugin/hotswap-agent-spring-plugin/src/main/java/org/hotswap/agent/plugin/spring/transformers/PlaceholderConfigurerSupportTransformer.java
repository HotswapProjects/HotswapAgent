/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

import java.util.Arrays;

public class PlaceholderConfigurerSupportTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PlaceholderConfigurerSupportTransformer.class);

    /**
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.config.PlaceholderConfigurerSupport")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        for (CtClass interfaceClazz : clazz.getInterfaces()) {
            if (interfaceClazz.getName().equals("org.hotswap.agent.plugin.spring.transformers.api.ValueResolverSupport")) {
                return;
            }
        }
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.ValueResolverSupport"));
        clazz.addField(CtField.make("private java.util.List _resolvers;", clazz), "new java.util.ArrayList(2)");
        clazz.addMethod(CtMethod.make("public java.util.List valueResolvers() { return this._resolvers; }", clazz));
        CtMethod ctMethod = clazz.getDeclaredMethod("doProcessProperties", new CtClass[]{classPool.get("org.springframework.beans.factory.config.ConfigurableListableBeanFactory"),
                classPool.get("org.springframework.util.StringValueResolver")});
        ctMethod.insertBefore("org.hotswap.agent.plugin.spring.reload.SpringChangedAgent.collectPlaceholderProperties($1); " +
                "this._resolvers.add($2);");
        LOGGER.debug("class 'org.springframework.beans.factory.config.PlaceholderConfigurerSupport' patched with placeholder keep.");
    }
}
