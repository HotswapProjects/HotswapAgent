/*
 * Copyright 2013-2019 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.weld.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into ProxyFactory constructors to register proxy factory into WeldPlugin. If WeldPlugin is not initialized in proxy factory
 * classLoader (ModuleClassLoader) then the proxy factory is not registered - it happens most likely in case of system beans proxy factories.
 *
 * @author Vladimir Dvorak
 */
public class ProxyFactoryTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyFactoryTransformer.class);

    /**
     * Patch ProxyFactory class.
     *   - add factory registration into constructor
     *   - changes call classLoader.loadClass(...) in getProxyClass() to ProxyClassLoadingDelegate.loadClass(classLoader, ...)
     *   - changes call ClassFileUtils.toClass() in createProxyClass() to ProxyClassLoadingDelegate.loadClass(...)
     *
     * @param ctClass the ProxyFactory class
     * @param classPool the class pool
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bean.proxy.ProxyFactory")
    public static void patchProxyFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("java.lang.Class"),
            classPool.get("java.util.Set"),
            //classPool.get("java.lang.String"),
            classPool.get("javax.enterprise.inject.spi.Bean"),
            classPool.get("boolean")
        };

        CtConstructor declaredConstructor = ctClass.getDeclaredConstructor(constructorParams);

        // TODO : we should find constructor without this() call and put registration only into this one
        declaredConstructor.insertAfter(
            "if (" + PluginManager.class.getName() + ".getInstance().isPluginInitialized(\"" + WeldPlugin.class.getName() + "\", this.classLoader)) {" +
                PluginManagerInvoker.buildCallPluginMethod("this.classLoader", WeldPlugin.class, "registerProxyFactory",
                        "this", "java.lang.Object",
                        "bean", "java.lang.Object",
                        "this.classLoader", "java.lang.ClassLoader",
                        "proxiedBeanType", "java.lang.Class"
                        ) +
            "}"
        );

        CtMethod getProxyClassMethod = ctClass.getDeclaredMethod("getProxyClass");
        getProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals(ClassLoader.class.getName()) && m.getMethodName().equals("loadClass"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.weld.command.ProxyClassLoadingDelegate.loadClass(this.classLoader,$1); }");
                    }
                });

        CtMethod createProxyClassMethod = ctClass.getDeclaredMethod("createProxyClass");
        createProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.jboss.weld.util.bytecode.ClassFileUtils") && m.getMethodName().equals("toClass"))
                            try {
                                if (m.getMethod().getParameterTypes().length == 3) {
                                    m.replace("{ $_ = org.hotswap.agent.plugin.weld.command.ProxyClassLoadingDelegate.toClass($$); }");
                                } else if (m.getMethod().getParameterTypes().length == 4) {
                                    LOGGER.debug("Proxy factory patch for delegating method skipped.", m.getClassName(), m.getMethodName());
                                } else {
                                    LOGGER.error("Method '{}.{}' patch failed. Unknown method arguments.", m.getClassName(), m.getMethodName());
                                }
                            } catch (NotFoundException e) {
                                LOGGER.error("Method '{}' not found in '{}'.", m.getMethodName(), m.getClassName());
                            }
                    }
                });
    }

}
