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

import java.lang.reflect.Method;

/**
 * Invoke methods on plugin manager, avoid classloader conflicts.
 * Each method has two variants - direct call or method source code builder.
 * <p/>
 * Note that because methods are invoked accross classloader, only parameters known to both classloaders
 * can be used. This is generally true for basic and java.* types.
 *
 * @author Jiri Bubnik
 */
public class PluginManagerInvoker {

    /**
     * Initialize plugin for a classloader.
     *
     * @param pluginClass    identify plugin instance
     * @param appClassLoader classloader in which the plugin should reside
     */
    public static <T> T callInitializePlugin(Class<T> pluginClass, ClassLoader appClassLoader) {
        // noinspection unchecked
        return (T) PluginManager.getInstance().getPluginRegistry().initializePlugin(
                pluginClass.getName(), appClassLoader
        );
    }


    public static String buildInitializePlugin(Class pluginClass) {
        return buildInitializePlugin(pluginClass, "getClass().getClassLoader()");
    }

    public static String buildInitializePlugin(Class pluginClass, String classLoaderVar) {
        return "org.hotswap.agent.config.PluginManager.getInstance().getPluginRegistry().initializePlugin(" +
                "\"" + pluginClass.getName() + "\", " + classLoaderVar +
                ");";
    }


    /**
     * Free all classloader references and close any associated plugin instance.
     * Typical use is after webapp undeploy.
     *
     * @param appClassLoader clasloade to free
     */
    public static void callCloseClassLoader(ClassLoader appClassLoader) {
        PluginManager.getInstance().closeClassLoader(appClassLoader);
    }

    public static String buildCallCloseClassLoader(String classLoaderVar) {
        return "org.hotswap.agent.config.PluginManager.getInstance().closeClassLoader(" + classLoaderVar + ");";
    }

    /**
     * Methods on plugin should be called via reflection, because the real plugin object is in parent classloader,
     * but plugin class may be defined in app classloader as well introducing ClassCastException on same class name.
     *
     * @param pluginClass    class name of the plugin - it is used to resolve plugin instance from plugin manager
     * @param appClassLoader application classloader (to resolve plugin instance)
     * @param method         method name
     * @param paramTypes     param types (as required by reflection)
     * @param params         actual param values
     * @return method return value
     */
    public static Object callPluginMethod(Class pluginClass, ClassLoader appClassLoader, String method, Class[] paramTypes, Object[] params) {
        Object pluginInstance = PluginManager.getInstance().getPlugin(pluginClass.getName(), appClassLoader);

        try {
            Method m = pluginInstance.getClass().getDeclaredMethod(method, paramTypes);
            return m.invoke(pluginInstance, params);
        } catch (Exception e) {
            throw new Error(String.format("Exception calling method %s on plugin class %s", method, pluginClass), e);
        }
    }

    /**
     * Equivalent to callPluginMethod for insertion into source code.
     * <p/>
     * PluginManagerInvoker.buildCallPluginMethod(this, "hibernateInitialized",
     * "getClass().getClassLoader()", "java.lang.ClassLoader")
     *
     * @param pluginClass       plugin to use
     * @param method            method name
     * @param paramValueAndType for each param its value AND type must be provided
     * @return method source code
     */
    public static String buildCallPluginMethod(Class pluginClass, String method, String... paramValueAndType) {
        return buildCallPluginMethod("getClass().getClassLoader()", pluginClass, method, paramValueAndType);
    }

    /**
     * Same as {@link PluginManagerInvoker#buildCallPluginMethod(Class, String, String...)}, but with explicit
     * appClassLoader variable. Use this method if appClassLoader is different from getClass().getClassLoader().
     */
    public static String buildCallPluginMethod(String appClassLoaderVar, Class pluginClass,
                                               String method, String... paramValueAndType) {

        String managerClass = PluginManager.class.getName();
        int paramCount = paramValueAndType.length / 2;

        StringBuilder b = new StringBuilder();

        // block to hide variables and catch checked exceptions
        b.append("try {");

        b.append("ClassLoader __pluginClassLoader = ");
        b.append(managerClass);
        b.append(".class.getClassLoader();");

        // Object __pluginInstance = org.hotswap.agent.config.PluginManager.getInstance().getPlugin(org.hotswap.agent.plugin.TestPlugin.class.getName(), __pluginClassLoader);
        b.append("Object __pluginInstance = ");
        b.append(managerClass);
        b.append(".getInstance().getPlugin(");
        b.append(pluginClass.getName());
        b.append(".class.getName(), " + appClassLoaderVar + ");");

        // Class __pluginClass = __pluginClassLoader.loadClass("org.hotswap.agent.plugin.TestPlugin");
        b.append("Class __pluginClass = ");
        b.append("__pluginClassLoader.loadClass(\"");
        b.append(pluginClass.getName());
        b.append("\");");

        // param types
        b.append("Class[] paramTypes = new Class[" + paramCount + "];");
        for (int i = 0; i < paramCount; i++) {
            // paramTypes[i] = = __pluginClassLoader.loadClass("my.test.TestClass").getClass();
            b.append("paramTypes[" + i + "] = __pluginClassLoader.loadClass(\"" + paramValueAndType[(i * 2) + 1] + "\");");
        }

        //   java.lang.reflect.Method __pluginMethod = __pluginClass.getDeclaredMethod("method", paramType1, paramType2);
        b.append("java.lang.reflect.Method __callPlugin = __pluginClass.getDeclaredMethod(\"");
        b.append(method);
        b.append("\", paramTypes");
        b.append(");");

        b.append("Object[] params = new Object[" + paramCount + "];");
        for (int i = 0; i < paramCount; i = i + 1) {
            b.append("params[" + i + "] = " + paramValueAndType[i * 2] + ";");
        }

        // __pluginMethod.invoke(__pluginInstance, param1, param2);
        b.append("__callPlugin.invoke(__pluginInstance, params);");

        // catch (Exception e) {throw new Error(e);}
        b.append("} catch (Exception e) {throw new Error(e);}");

        return b.toString();
    }
}
