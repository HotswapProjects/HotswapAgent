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
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.support.InitMethodEnhance;

public class InitDestroyAnnotationBeanPostProcessorTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(
        InitDestroyAnnotationBeanPostProcessorTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        LOGGER.debug(
            "Class 'org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor' patched with"
                + " catch exception.");
        clazz.addField(CtField.make("private static final org.hotswap.agent.logging.AgentLogger $$ha$LOGGER = " +
                "org.hotswap.agent.logging.AgentLogger.getLogger(org.springframework.beans.factory.annotation"
                + ".InitDestroyAnnotationBeanPostProcessor.class);",
            clazz));
        CtMethod method = clazz.getDeclaredMethod("postProcessBeforeInitialization",
            new CtClass[] {classPool.get("java.lang.Object"), classPool.get("java.lang.String")});
        String code = "{"
            + "if (!org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant.existReload()) {"
                + " throw $e ; }"
            + "else {"
                + "if ($$ha$LOGGER.isDebugEnabled()) {"
                    + "$$ha$LOGGER.debug(\"Failed to invoke init method of {} from @PostConstructor: {}\", $e, "
                        + "new java.lang.Object[]{$1.getClass().getName(),$e.getMessage()});"
                + "} else {"
                    + "$$ha$LOGGER.warning(\"Failed to invoke init method of {} from @PostConstructor: {}\", "
                        + "new java.lang.Object[]{$1.getClass().getName(),$e.getMessage()});"
                + "}"
                + "return $1;"
            + "}"
            + "}";
        method.addCatch(code, classPool.get("org.springframework.beans.factory.BeanCreationException"));
    }
}
