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
package org.hotswap.agent.util;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.testData.SimplePlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Jiri Bubnik
 */
public class PluginManagerInvokerTest {

    @Test
    public void testBuildCallPluginMethod() throws Exception {
        SimplePlugin plugin = new SimplePlugin();
        registerPlugin(plugin);
//        plugin.init(PluginManager.getInstance());

        String s = PluginManagerInvoker.buildCallPluginMethod(plugin.getClass(),
                "callPluginMethod",
                "Boolean.TRUE", "java.lang.Boolean");

        ClassPool classPool = ClassPool.getDefault();
        classPool.appendSystemPath();

        CtClass clazz = classPool.makeClass("Test");
        clazz.addMethod(CtNewMethod.make("public void test() {" + s + "}", clazz));
        Class<?> testClass = clazz.toClass();


        Method testMethod = testClass.getDeclaredMethod("test");
        testMethod.invoke(testClass.getDeclaredConstructor().newInstance());


    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * The inserted code may run in a class of another classloader (e.g. a server module) and call the plugin for
     * an app classloader, which disabled the plugin. The plugin is not initialized there, skip the call.
     */
    @Test
    public void testBuildCallPluginMethodSkipsPluginDisabledInAppClassLoader() throws Exception {
        registerPlugin(new SimplePlugin());

        Method testMethod = makeCallPluginMethod("TestDisabled");

        ClassLoader disabledLoader = configuredClassLoader("disabledPlugins=Hibernate plugin");
        ClassLoader enabledLoader = configuredClassLoader("disabledPlugins=");
        try {
            // no exception, the call is skipped
            testMethod.invoke(null, disabledLoader);
            assertNull(PluginManagerInvoker.callPluginMethod(SimplePlugin.class, disabledLoader,
                    "callPluginMethod", new Class[]{Boolean.class}, new Object[]{Boolean.TRUE}));

            // still an error if the plugin is enabled, but not initialized
            try {
                testMethod.invoke(null, enabledLoader);
                fail("Plugin is not initialized in the classloader, the call should fail");
            } catch (InvocationTargetException e) {
                assertEquals(Error.class, e.getCause().getClass());
            }
        } finally {
            removeConfiguration(disabledLoader);
            removeConfiguration(enabledLoader);
        }
    }

    private Method makeCallPluginMethod(String className) throws Exception {
        String s = PluginManagerInvoker.buildCallPluginMethod("$1", SimplePlugin.class,
                "callPluginMethod",
                "Boolean.TRUE", "java.lang.Boolean");

        ClassPool classPool = ClassPool.getDefault();
        classPool.appendSystemPath();

        CtClass clazz = classPool.makeClass(className);
        clazz.addMethod(CtNewMethod.make("public static void test(ClassLoader appClassLoader) {" + s + "}", clazz));
        return clazz.toClass().getDeclaredMethod("test", ClassLoader.class);
    }

    // app classloader with its own hotswap-agent.properties, registered to the plugin manager
    private ClassLoader configuredClassLoader(String properties) throws Exception {
        File dir = temporaryFolder.newFolder();
        Files.write(new File(dir, "hotswap-agent.properties").toPath(), properties.getBytes(StandardCharsets.UTF_8));
        ClassLoader classLoader = new URLClassLoader(new URL[]{dir.toURI().toURL()}, null);
        configurations().put(classLoader, new PluginConfiguration(null, classLoader, false));
        return classLoader;
    }

    private void removeConfiguration(ClassLoader classLoader) throws Exception {
        configurations().remove(classLoader);
    }

    // configurations are not public, use reflection to insert test data
    private Map<ClassLoader, PluginConfiguration> configurations() throws Exception {
        Field f = PluginManager.class.getDeclaredField("classLoaderConfigurations");
        f.setAccessible(true);
        // noinspection unchecked
        return (Map<ClassLoader, PluginConfiguration>) f.get(PluginManager.getInstance());
    }

    // plugin registration is not public, use reflection to insert test data
    private void registerPlugin(Object plugin) throws NoSuchFieldException, IllegalAccessException {
        Field f = PluginRegistry.class.getDeclaredField("registeredPlugins");
        f.setAccessible(true);
        // noinspection unchecked
        Map<Class, Map<ClassLoader, Object>> registeredPlugins =
                (Map<Class, Map<ClassLoader, Object>>) f.get(PluginManager.getInstance().getPluginRegistry());
        registeredPlugins.put(plugin.getClass(), Collections.singletonMap(getClass().getClassLoader(), plugin));
    }
}
