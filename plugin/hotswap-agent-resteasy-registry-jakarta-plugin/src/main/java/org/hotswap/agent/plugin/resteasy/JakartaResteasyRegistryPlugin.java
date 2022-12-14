/*
 * Copyright 2013-2022 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.resteasy;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.AccessFlag;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.ClassName;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.util.classpool.ClassPoolHelper;


/**
 * RESTeasy plugin which cleanups and registers class redefinitions in the RESTeasy ResourceMethodRegistry
 *
 * @author alpapad@gmail.com
 *
 */
@Plugin(name = "JakartaResteasyRegistry", //
        description = "Jboss RESTeasy Reload ResourceMethodRegistry if @Path annotated class is changed.", //
        testedVersions = { "6.2.1.Final" }, //
        expectedVersions = { "6.2.1" })
public class JakartaResteasyRegistryPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JakartaResteasyRegistryPlugin.class);

    private static final String PATH_ANNOTATION = "jakarta.ws.rs.Path";

    @Init
    ClassLoader appClassLoader;

    @Init
    Scheduler scheduler;

    Object servletContext;

    Object servletContainerDispatcher;

    /**
     * Patch ResourceMethodRegistry, make rootNode && root fields public
     *
     * @param ctClass
     * @param classPool
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.resteasy.core.ResourceMethodRegistry")
    public static void patchResourceMethodRegistry(CtClass ctClass, ClassPool classPool) {
        if (!ClassPoolHelper.hasBeenRead(classPool, ClassName.JAKARTA_SERVLET)) {
            return;
        }
        try {
            // Make ResourceMethodRegistry root nodes readable
            ctClass.getField("rootNode").setModifiers(AccessFlag.PUBLIC);
            ctClass.getField("root").setModifiers(AccessFlag.PUBLIC);
        } catch (NotFoundException e) {
            LOGGER.error("Error patching FilterDispatcher", e);
        }
    }

    /**
     *
     * @param ctClass
     * @param classPool
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.resteasy.plugins.server.servlet.FilterDispatcher")
    public static void patchFilterDispatcher(CtClass ctClass, ClassPool classPool) {
        if (!ClassPoolHelper.hasBeenRead(classPool, ClassName.JAKARTA_SERVLET)) {
            return;
        }
        try{
            CtMethod init = ctClass.getDeclaredMethod("init");
            init.insertAfter(""//
                    +"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" //
                    +"java.lang.Object $$servletContext = servletConfig.getServletContext();"//
                    + PluginManagerInvoker.buildInitializePlugin(JakartaResteasyRegistryPlugin.class, "$$cl")//
                    + PluginManagerInvoker.buildCallPluginMethod("$$cl", JakartaResteasyRegistryPlugin.class,
                            "registerContext", "$$servletContext", "java.lang.Object")//
                    + PluginManagerInvoker.buildCallPluginMethod("$$cl", JakartaResteasyRegistryPlugin.class,
                            "registerServletContainerDispatcher", "servletContainerDispatcher", "java.lang.Object")//
            );
        } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching FilterDispatcher", e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher")
    public static void patchServletDispatcher(CtClass ctClass, ClassPool classPool){
        if (!ClassPoolHelper.hasBeenRead(classPool, ClassName.JAKARTA_SERVLET)) {
            return;
        }
        try{
            CtMethod init = ctClass.getDeclaredMethod("init");

            init.insertAfter(""//
                    + "java.lang.Object $$servletContext = servletConfig.getServletContext();" //
                    + "java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"//
                    + PluginManagerInvoker.buildInitializePlugin(JakartaResteasyRegistryPlugin.class, "$$cl") //
                    + PluginManagerInvoker.buildCallPluginMethod("$$cl", JakartaResteasyRegistryPlugin.class,
                            "registerContext", "$$servletContext", "java.lang.Object") //
                    + PluginManagerInvoker.buildCallPluginMethod("$$cl", JakartaResteasyRegistryPlugin.class,
                            "registerServletContainerDispatcher", "servletContainerDispatcher", "java.lang.Object")//
            );
        } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching HttpServletDispatcher", e);
        }
    }


    public void registerContext(Object servletContext) {
        this.servletContext = servletContext;
        LOGGER.info("Registered ServletContext {} ", servletContext);
    }

    public void registerServletContainerDispatcher(Object servletContainerDispatcher) {
        this.servletContainerDispatcher = servletContainerDispatcher;
        LOGGER.info("Registered ServletContainerDispatcher {} ", servletContainerDispatcher);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void entityReload(ClassLoader classLoader, CtClass clazz, Class<?> original) {
        if (!ClassLoaderHelper.isClassLoaded(classLoader, ClassName.JAKARTA_SERVLET)) {
            return;
        }
        if (AnnotationHelper.hasAnnotation(original, PATH_ANNOTATION)
                || AnnotationHelper.hasAnnotation(clazz, PATH_ANNOTATION)) {
            if(LOGGER.isLevelEnabled(Level.TRACE)) {
                LOGGER.trace("Reload @Path annotated class {}", clazz.getName());
            }
            refreshClass(classLoader, clazz.getName(), original, 250);
        }
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = { FileEvent.CREATE })
    public void newEntity(ClassLoader classLoader, CtClass clazz) throws Exception {
        if (!ClassLoaderHelper.isClassLoaded(classLoader, ClassName.JAKARTA_SERVLET)) {
            return;
        }
        if (AnnotationHelper.hasAnnotation(clazz, PATH_ANNOTATION)) {
            if(LOGGER.isLevelEnabled(Level.TRACE)) {
                LOGGER.trace("Load @Path annotated class {}", clazz.getName());
            }
            refreshClass(classLoader, clazz.getName(), null, 500);
        }
    }

    private void refreshClass(ClassLoader classLoader, String name, Class<?> original, int timeout) {
        try {
            Class<?> cmdClass = Class.forName(RefreshRegistryCommand.class.getName(), true, appClassLoader);
            Command cmd = (Command) cmdClass.newInstance();
            ReflectionHelper.invoke(cmd, cmdClass, "setupCmd",
                    new Class[] { ClassLoader.class, Object.class, Object.class, String.class, java.lang.Class.class },
                    classLoader, servletContext, servletContainerDispatcher, name, original);
            scheduler.scheduleCommand(cmd, timeout);
        } catch (Exception e) {
            LOGGER.error("refreshClass() exception {}.", e.getMessage());
        }
    }

    @Init
    public void initializeInstance(PluginConfiguration pluginConfiguration) {
        LOGGER.info("ResteasyRegistry plugin initializing");
    }
}
