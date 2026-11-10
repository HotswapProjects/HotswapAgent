/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.wildfly.el;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Versions;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.Scheduler.DuplicateSheduleBehaviour;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Clear the bean properties cache of javax.el.BeanELResolver and jakarta.el.BeanELResolver
 * (org.jboss.el.cache.BeanPropertiesCache) after any class redefinition.
 *
 * @author alpapad@gmail.com
 *
 */
@Plugin(name = "WildFlyELResolver",
        group = "groupELResolver",
        description = "Purge WildFlyELResolver class cache on any class redefinition.",
        testedVersions = { "1.0.5.Final" },
        expectedVersions = { "1.0.5.Final" })
@Versions(
    maven = {
        @Maven(value = "[1.0,)", artifactId = "jboss-el-api_3.0_spec", groupId = "org.jboss.spec.javax.el"),
        @Maven(value = "[1.0,)", artifactId = "jboss-el-api_5.0_spec", groupId = "org.jboss.spec.jakarta.el"),
    }
)
public class WildFlyELResolverPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WildFlyELResolverPlugin.class);

    public static final String PURGE_CLASS_CACHE_METHOD_NAME = "$$ha$resetCache";

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    /**
     * Hook on BeanELResolver class and for each instance: - ensure plugin is
     * initialized - register instances using registerBeanELResolver() method.
     *
     * @param ctClass
     *            the ct class
     * @param classPool
     *            the class pool
     * @throws CannotCompileException
     *             the cannot compile exception
     * @throws NotFoundException
     *             the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "(javax|jakarta).el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter("java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" + PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class, "$$cl"));
        }

        LOGGER.info("Patched JbossELResolver");
    }

    /**
     * Hook on BeanELResolver class and for each instance: - ensure plugin is
     * initialized - register instances using registerBeanELResolver() method.
     *
     * @param ctClass
     *            the ct class
     * @param classPool
     *            the class pool
     * @throws CannotCompileException
     *             the cannot compile exception
     * @throws NotFoundException
     *             the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.cache.BeanPropertiesCache")
    public static void beanPropertiesCache(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter("java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" + PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class, "$$cl"));
        }
        LOGGER.info("Patched org.jboss.el.cache.BeanPropertiesCache");
    }

    /**
     * Hook on BeanELResolver class and for each instance: - ensure plugin is
     * initialized - register instances using registerBeanELResolver() method.
     *
     * @param ctClass
     *            the ct class
     * @param classPool
     *            the class pool
     * @throws CannotCompileException
     *             the cannot compile exception
     * @throws NotFoundException
     *             the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.cache.FactoryFinderCache")
    public static void factoryFinderCache(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter("java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" + //
                    PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class, "$$cl"));
        }
        LOGGER.info("Patched org.jboss.el.cache.FactoryFinderCache");
    }

    /**
     * Invalidate class cache.
     *
     * @param original
     *            the original
     * @throws Exception
     *             the exception
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache(Class<?> original) throws Exception {
        LOGGER.trace("Running invalidateClassCache {}", appClassLoader);
        if (original.getClassLoader() == null) {
            return;
        }
        // Clear all classes of the class loader, the properties of other classes (subclasses, proxies) may change too.
        // The commands of classes reloaded together are equal, the first one clears them all.
        PurgeWildFlyBeanELResolverCacheCommand cmd = new PurgeWildFlyBeanELResolverCacheCommand(appClassLoader, original.getClassLoader());
        scheduler.scheduleCommand(cmd, 250, DuplicateSheduleBehaviour.SKIP);
    }

    /**
     * Initialize instance.
     *
     * @param pluginConfiguration
     *            the plugin configuration
     */
    @Init
    public void initializeInstance(PluginConfiguration pluginConfiguration) {
        LOGGER.info("WildFlyELResolver plugin initialized");
    }
}