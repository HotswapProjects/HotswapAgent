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
package org.hotswap.agent.util;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.testData.SimplePlugin;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

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
        testMethod.invoke(testClass.newInstance());


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
