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
package org.hotswap.agent.plugin.proxy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.hscglib.CglibEnhancerProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParams;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatcherFactory;

/**
 * Redefines proxy classes that implement or extend changed interfaces or classes. Currently it supports proxies created
 * with Java reflection and the Cglib library.
 *
 * @author Erki Ehtla, Vladimir Dvorak
 *
 */
@Plugin(name = "Proxy", description = "Redefines proxies", testedVersions = { "" }, expectedVersions = { "all" }, supportClass = RedefinitionScheduler.class)
public class ProxyPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyPlugin.class);
    static boolean isJava8OrNewer = WatcherFactory.JAVA_VERSION >= 18;

    /**
     * Flag to check reload status. In unit test we need to wait for reload
     * finish before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    private static Set<String> proxyRedefiningMap = ConcurrentHashMap.newKeySet();

    @OnClassLoadEvent(classNameRegexp = "(jdk.proxy\\d+.\\$Proxy.*)|(com.sun.proxy.\\$Proxy.*)", events = LoadEvent.REDEFINE, skipSynthetic = false)
    public static void transformJavaProxy(final Class<?> classBeingRedefined, final ClassLoader classLoader) {

    /*
     * Proxy can't be redefined directly in this method (and return new proxy class bytes), since the classLoader contains
     * OLD definition of proxie's interface. Therefore proxy is defined in deferred command (after some delay)
     * after proxied interface is redefined in DCEVM.
     */
        Object proxyCache = ReflectionHelper.getNoException(null, java.lang.reflect.Proxy.class, "proxyCache");

        if (proxyCache != null) {
            try {
                ReflectionHelper.invoke(proxyCache, proxyCache.getClass().getSuperclass(), "removeAll",
                        new Class[] { ClassLoader.class }, classLoader);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Reflection proxy cache flush failed. {}", e.getMessage());
            }
        }

        if (!ClassLoaderHelper.isClassLoderStarted(classLoader)) {
            return;
        }

        final String className = classBeingRedefined.getName();

        if (proxyRedefiningMap.contains(className)) {
            proxyRedefiningMap.remove(className);
            return;
        }

        proxyRedefiningMap.add(className);

        final Map<String, String> signatureMapOrig = ProxyClassSignatureHelper.getNonSyntheticSignatureMap(classBeingRedefined);

        reloadFlag = true;

        // TODO: can be single command if scheduler guarantees the keeping execution order in the order of redefinition
        PluginManager.getInstance().getScheduler().scheduleCommand(new ReloadJavaProxyCommand(classLoader, className, signatureMapOrig), 50);
    }

    public static void removeProxyDefiningClassName(String className) {
        proxyRedefiningMap.remove(className);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
    public static byte[] transformCglibProxy(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
            final ClassLoader loader, final ClassPool cp) throws Exception {

        GeneratorParams generatorParams = GeneratorParametersTransformer.getGeneratorParams(loader, classBeingRedefined.getName());

        if (generatorParams == null) {
            return classfileBuffer;
        }

        if (!ClassLoaderHelper.isClassLoderStarted(loader)) {
            return classfileBuffer;
        }

        loader.loadClass("java.beans.Introspector").getMethod("flushCaches").invoke(null);
        if (generatorParams.getParam().getClass().getName().endsWith(".Enhancer")) {
            try {
                return CglibEnhancerProxyTransformer.transform(classBeingRedefined, cp, classfileBuffer, loader, generatorParams);
            } catch (Exception e) {
                LOGGER.error("Error redifining Cglib Enhancer proxy {}", e, classBeingRedefined.getName());
            }
        }

        // Multistep transformation crashed jvm in java8 u05
        if (!isJava8OrNewer) {
            try {
                return CglibProxyTransformer.transform(classBeingRedefined, cp, classfileBuffer, generatorParams);
            }
            catch (Exception e) {
                LOGGER.error("Error redifining Cglib proxy {}", e, classBeingRedefined.getName());
            }
        }

        return classfileBuffer;
    }

    /**
     * Modifies Cglib bytecode generators to store the parameters for this plugin
     *
     * @throws Exception
     */
    @OnClassLoadEvent(classNameRegexp = ".*/cglib/.*", skipSynthetic = false)
    public static CtClass transformDefinitions(CtClass cc) throws Exception {
        try {
            return GeneratorParametersTransformer.transform(cc);
        } catch (Exception e) {
            LOGGER.error("Error modifying class for cglib proxy creation parameter recording", e);
        }
        return cc;
    }
}
