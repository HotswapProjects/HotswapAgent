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
package org.hotswap.agent.plugin.spring.reader;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class AnnotatedBeanDefinitionReaderTransformer {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(AnnotatedBeanDefinitionReaderTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.annotation.AnnotatedBeanDefinitionReader")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod method = clazz.getDeclaredMethod("register", new CtClass[]{classPool.get(Class.class.getName() + "[]")});
        method.insertAfter("org.hotswap.agent.plugin.spring.reader.AnnotatedBeanDefinitionReaderAgent.getInstance($0).register($1);");
        LOGGER.debug("Class 'org.springframework.context.annotation.AnnotatedBeanDefinitionReader' patched");
    }
}
