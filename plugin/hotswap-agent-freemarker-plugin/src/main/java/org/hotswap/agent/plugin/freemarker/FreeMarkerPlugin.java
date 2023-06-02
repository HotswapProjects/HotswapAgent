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
package org.hotswap.agent.plugin.freemarker;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "FreeMarker",
        description = "Clear FreeMarker bean class introspection cache when class files are redefined. And enhance " +
                "FreeMarkerConfigurationFactory to support hotswap without rebuilding project",
        testedVersions = {"FreeMarker: 2.3.28; spring 4.3.12"},
        expectedVersions = {"FreeMarker: 2.3.20+; spring 4.3.12"}
)
public class FreeMarkerPlugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(FreeMarkerPlugin.class);

    @Init
    private Scheduler scheduler;

    private final Command clearIntrospectionCache = new Command() {
        @Override
        public void executeCommand() {
            LOGGER.debug("Clearing FreeMarker BeanWrapper class introspection class.");
            try {
                Object config = ReflectionHelper.get(freeMarkerServlet, "config");
                Object objectWrapper = ReflectionHelper.get(config, "objectWrapper");
                ReflectionHelper.invoke(objectWrapper, objectWrapper.getClass(), "clearClassIntrospecitonCache", new Class[]{});
                LOGGER.info("Cleared FreeMarker introspection cache");
            } catch (Exception e) {
                LOGGER.error("Error clearing FreeMarker introspection cache", e);
            }
        }
    };

    private Object freeMarkerServlet;

    @OnClassLoadEvent(classNameRegexp = "freemarker.ext.servlet.FreemarkerServlet")
    public static void init(ClassPool classPool, final CtClass ctClass) throws NotFoundException, CannotCompileException {

        String src = PluginManagerInvoker.buildInitializePlugin(FreeMarkerPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(FreeMarkerPlugin.class,
                "registerServlet", "this", "java.lang.Object");
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(src);

        LOGGER.debug("Patched freemarker.ext.servlet.FreemarkerServlet");
    }

    public void registerServlet(final Object freeMarkerServlet) {
        this.freeMarkerServlet = freeMarkerServlet;
        LOGGER.info("Plugin {} initialized for servlet {}", getClass(), this.freeMarkerServlet);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void cacheReloader(CtClass ctClass) {
        scheduler.scheduleCommand(clearIntrospectionCache, 500);
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.ui.freemarker.FreeMarkerConfigurationFactory")
    public static void patchCreateConfiguration(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("createConfiguration", new CtClass[]{});
            method.insertBefore("this.preferFileSystemAccess = false;");
            method.insertBefore("String[] $ha$newArray = new String[this.templateLoaderPaths.length + 1]; " +
                    "$ha$newArray[0] = \"classpath:/$ha$freemarker\";" +
                    "for (int i = 0; i < this.templateLoaderPaths.length; i++) {" +
                    "    $ha$newArray[i + 1] = this.templateLoaderPaths[i];" +
                    "}" + "this.templateLoaderPaths = $ha$newArray;");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch patchCreateConfiguration method for {}", ctClass.getName(), e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "freemarker.template.Configuration")
    public static void patchCreateTemplateCache(ClassPool classPool, final CtClass ctClass) {
        try {
            CtMethod method = ctClass.getDeclaredMethod("createTemplateCache", new CtClass[]{});
            method.insertAfter("cache.setDelay(0L);");
        } catch (NotFoundException | CannotCompileException e) {
            LOGGER.debug("Cannot patch patchCreateTemplateCache method for {}", ctClass.getName(), e);
        }
    }
}