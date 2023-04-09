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
package org.hotswap.agent.plugin.idea;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * IntelliJ IDEA plugin
 *
 * @author Vladimir Dvorak
 *
 */
@Plugin(name = "Idea",
        description = "IntelliJ Idea plugin",
        testedVersions = { "" },
        expectedVersions = { "all" }
)
public class IdeaPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(IdeaPlugin.class);

    private static boolean initialized = false;

    public void init() {
        if (!initialized) {
            initialized = true;
            LOGGER.info("Idea plugin plugin initialized.");
        }
    }

    @OnClassLoadEvent(classNameRegexp = "com.intellij.util.lang.UrlClassLoader")
    public static void patchUrlClassLoader(CtClass ctClass) throws CannotCompileException {

        if (!initialized) {
          String initializePlugin = PluginManagerInvoker.buildInitializePlugin(IdeaPlugin.class, "appClassLoader");
          String initializeThis = PluginManagerInvoker.buildCallPluginMethod("appClassLoader", IdeaPlugin.class, "init");

          for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
              constructor.insertAfter(initializePlugin);
              constructor.insertAfter(initializeThis);
          }
        }

        try {
            ctClass.getDeclaredMethod("findClass").insertBefore(
                "if ($1.startsWith(\"org.hotswap.agent\")) { " +
                    "return appClassLoader.loadClass($1);" +
                "}"
            );
        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find method \"findClass()\" in com.intellij.util.lang.UrlClassLoader.", e);
        }

        try {
            ctClass.getDeclaredMethod("getResourceAsStream").insertBefore(
                "if ($1.startsWith(\"org/hotswap/agent\")) { " +
                    "return appClassLoader.getResourceAsStream($1);" +
                "}"
            );
        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find method \"getResourceAsStream()\" in com.intellij.util.lang.UrlClassLoader.", e);
        }
        ctClass.addMethod(CtNewMethod.make(
            "public java.net.URL getResource(String name) {" +
                "if (name.startsWith(\"org/hotswap/agent/\")) { " +
                    "return appClassLoader.getResource(name);" +
                "}" +
                "return super.getResource(name);" +
            "}", ctClass)
        );
    }

}
