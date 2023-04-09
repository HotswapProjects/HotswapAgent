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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Hook into AbstractProxyFactory constructors to register proxy factory into OwbPlugin. If OwbPlugin is not initialized in proxy factory
 * classLoader (ModuleClassLoader) then the proxy factory is not registered - it happens most likely in case of system beans proxy factories.
 *
 * @author Vladimir Dvorak
 */
public class ProxyFactoryTransformer {

    /**
     * Patch AbstractProxyFactory class.
     *   - add factory registration into constructor
     *   - changes call classLoader.loadClass(...) in getProxyClass() to ProxyClassLoadingDelegate.loadClass(classLoader, ...)
     *   - changes call ClassFileUtils.toClass() in createProxyClass() to ProxyClassLoadingDelegate.loadClass(...)
     *
     * @param ctClass the ProxyFactory class
     * @param classPool the class pool
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.proxy.AbstractProxyFactory")
    public static void patchProxyFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        CtMethod getProxyClassMethod = ctClass.getDeclaredMethod("getUnusedProxyClassName");
        getProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals(Class.class.getName()) && m.getMethodName().equals("forName"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.owb.command.ProxyClassLoadingDelegate.forName($$); }");
                    }
                });

        CtMethod createProxyClassMethod = ctClass.getDeclaredMethod("createProxyClass", new CtClass[] {
                classPool.get(ClassLoader.class.getName()),
                classPool.get(String.class.getName()),
                classPool.get(Class.class.getName()),
                classPool.get(Method.class.getName() + "[]"),
                classPool.get(Method.class.getName() + "[]"),
                classPool.get(Constructor.class.getName())
            }
        );

        createProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("defineAndLoadClass"))
                            if ("org.apache.webbeans.proxy.Unsafe".equals(m.getClassName())) {
                                // OWB >= 2.0.8
                                m.replace("{ $_ = org.hotswap.agent.plugin.owb.command.ProxyClassLoadingDelegate.defineAndLoadClassWithUnsafe(this.unsafe, $$); }");
                            } else {
                                // OWB < 2.0.8
                                m.replace("{ $_ = org.hotswap.agent.plugin.owb.command.ProxyClassLoadingDelegate.defineAndLoadClass(this, $$); }");
                            }
                    }
                });
    }

}
