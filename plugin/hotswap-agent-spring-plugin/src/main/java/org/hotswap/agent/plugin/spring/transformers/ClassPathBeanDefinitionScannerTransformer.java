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
package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;

/**
 * Hook into classpath scanner process to register basicPackage of scanned classes.
 *
 * Catch changes on component-scan configuration such as (see tests):
 * <pre>&lt;context:component-scan base-package="org.hotswap.agent.plugin.spring.testBeans"/&gt;</pre>
 */
public class ClassPathBeanDefinitionScannerTransformer {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanDefinitionScannerTransformer.class);

    /**
     * Insert at the beginning of the method:
     * <pre>public Set<BeanDefinition> findCandidateComponents(String basePackage)</pre>
     * new code to initialize ClassPathBeanDefinitionScannerAgent for a base class
     * It would be better to override a more appropriate method
     * org.springframework.context.annotation.ClassPathBeanDefinitionScanner.scan() directly,
     * however there are issues with javassist and varargs parameters.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (SpringPlugin.basePackagePrefixes == null) {
            CtMethod method = clazz.getDeclaredMethod("findCandidateComponents", new CtClass[]{classPool.get("java.lang.String")});
            method.insertAfter(
                    "if (this instanceof org.springframework.context.annotation.ClassPathBeanDefinitionScanner) {" +
                    "  if (org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent.getInstance($1) == null) {" +
                    "    org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent.getInstance(" +
                            "(org.springframework.context.annotation.ClassPathBeanDefinitionScanner)this).registerBasePackage($1);" +
                    "  }" +
                    "}");

            LOGGER.debug("Class 'org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider' " +
                    "patched with basePackage registration.");
        } else {
            LOGGER.debug("No need to register scanned path, instead just register 'spring.basePackagePrefix' in " +
                    "configuration file.");
        }
    }
}
