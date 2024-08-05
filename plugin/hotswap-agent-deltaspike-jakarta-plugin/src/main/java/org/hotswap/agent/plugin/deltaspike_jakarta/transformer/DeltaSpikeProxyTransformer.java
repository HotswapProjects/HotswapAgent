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
package org.hotswap.agent.plugin.deltaspike_jakarta.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Register proxy factory to DeltaSpikePlugin, patch AsmProxyClassGenerator
 *
 * @author Vladimir Dvorak
 */
public class DeltaSpikeProxyTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikeProxyTransformer.class);

    /**
     * Delegates ClassUtils.tryToLoadClassForName to org.hotswap.agent.plugin.deltaspike_jakarta.command.ProxyClassLoadingDelegate::tryToLoadClassForName
     *
     * @param classPool the class pool
     * @param ctClass   the ct class
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.api.DeltaSpikeProxyFactory")
    public static void patchDeltaSpikeProxyFactory(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        instrumentTryToLoadClassForName(ctClass, "resolveAlreadyDefinedProxyClass");
    }

    private static void instrumentTryToLoadClassForName(CtClass ctClass, String methodName) throws CannotCompileException {
        try {
            CtMethod getProxyClassMethod = ctClass.getDeclaredMethod(methodName);
            getProxyClassMethod.instrument(
                    new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals("org.apache.deltaspike.core.util.ClassUtils") && m.getMethodName().equals("tryToLoadClassForName"))
                                m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike_jakarta.command.ProxyClassLoadingDelegate.tryToLoadClassForName($$); }");
                        }
                    });
        } catch (NotFoundException e) {
            LOGGER.debug("Method '{}' not found in '{}'.", methodName, ctClass.getName());
        }
    }

    /**
     * Patch asm delta spike proxy class generator.
     *
     * @param classPool the class pool
     * @param ctClass   the ct class
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.impl.AsmDeltaSpikeProxyClassGenerator")
    public static void patchAsmDeltaSpikeProxyClassGenerator(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        CtMethod generateProxyClassMethod = ctClass.getDeclaredMethod("generateProxyClass");
        generateProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.apache.deltaspike.proxy.impl.AsmDeltaSpikeProxyClassGenerator") && m.getMethodName().equals("loadClass")) {
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike_jakarta.command.ProxyClassLoadingDelegate.loadClass($$); }");
                        } else if (m.getClassName().equals("org.apache.deltaspike.proxy.impl.ClassDefiner") && m.getMethodName().equals("defineClass")) {
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike_jakarta.command.ProxyClassLoadingDelegate.defineClass($$); }");
                        }
                    }
                });
        LOGGER.debug("org.apache.deltaspike.proxy.impl.AsmDeltaSpikeProxyClassGenerator patched.");
    }


}
