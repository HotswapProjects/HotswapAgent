/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.hotswap.agent.plugin.resteasy;


import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

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
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Resteasy
 *
 *  @author Vladimir Dvorak - HotswapAgent implementation,
 *  @author Stuart Douglas - original concept in FakeReplace
 */
@Plugin(name = "Resteasy",
        description = "Jboss RESTeasy framework (http://resteasy.jboss.org/). Reload FilterDispatcher / HttpServletDispatcher configurations "
                + "if @Path annotated class is changed.",
        testedVersions = {"3.0.14.Final"},
        expectedVersions = {"All between 2.x - 3.x"}
        )
public class ResteasyPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ResteasyPlugin.class);

    private static final String PATH_ANNOTATION = "javax.ws.rs.Path";

    public static final String FIELD_NAME = "__config";
    public static final String PARAMETER_FIELD_NAME = "__params";

    @Init
    ClassLoader appClassLoader;

    @Init
    Scheduler scheduler;

    Set<Object> registeredDispatchers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Resteasy plugin initialized.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.resteasy.plugins.server.servlet.FilterDispatcher")
    public static void patchFilterDispatcher(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass fltCfgClass = classPool.get("javax.servlet.FilterConfig");
        CtField configField = new CtField(fltCfgClass, FIELD_NAME, ctClass);
        ctClass.addField(configField);

        CtClass setClass = classPool.get(java.util.Set.class.getName());
        CtField paramsField = new CtField(setClass, PARAMETER_FIELD_NAME, ctClass);
        ctClass.addField(paramsField);

        CtMethod methInit = ctClass.getDeclaredMethod("init");
        methInit.insertBefore(
                "{" +
                "   if(this." + PARAMETER_FIELD_NAME + " == null) {" +
                        PluginManagerInvoker.buildInitializePlugin(ResteasyPlugin.class) +
                        PluginManagerInvoker.buildCallPluginMethod(ResteasyPlugin.class, "registerDispatcher", "this", "java.lang.Object") +
                "   }" +
                "   this." + FIELD_NAME + " = $1;" +
                "   this." + PARAMETER_FIELD_NAME + " = " +
                        ResteasyContextParams.class.getName() + ".init($1.getServletContext(), this." + PARAMETER_FIELD_NAME +"); " +
                "}"
        );

    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher")
    public static void patchServletDispatcher(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass fltCfgClass = classPool.get("javax.servlet.ServletConfig");
        CtField configField = new CtField(fltCfgClass, FIELD_NAME, ctClass);
        ctClass.addField(configField);

        CtClass setClass = classPool.get(java.util.Set.class.getName());
        CtField paramsField = new CtField(setClass, PARAMETER_FIELD_NAME, ctClass);
        ctClass.addField(paramsField);

        CtMethod methInit = ctClass.getDeclaredMethod("init");
        methInit.insertBefore(
                "{" +
                "   if(this." + PARAMETER_FIELD_NAME + " == null) {" +
                        PluginManagerInvoker.buildInitializePlugin(ResteasyPlugin.class) +
                        PluginManagerInvoker.buildCallPluginMethod(ResteasyPlugin.class, "registerDispatcher", "this", "java.lang.Object") +
                "   }" +
                "   this." + FIELD_NAME + " = $1;" +
                "   this." + PARAMETER_FIELD_NAME + " = " +
                        ResteasyContextParams.class.getName() + ".init($1.getServletContext(), this." + PARAMETER_FIELD_NAME +"); " +
                "}"
        );

    }

    public void registerDispatcher(Object filterDispatcher) {
        registeredDispatchers.add(filterDispatcher);
        LOGGER.debug("RestEasyPlugin - dispatcher registered : " + filterDispatcher.getClass().getName());
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void entityReload(ClassLoader classLoader, CtClass clazz, Class original) {
        if (AnnotationHelper.hasAnnotation(original, PATH_ANNOTATION)
                || AnnotationHelper.hasAnnotation(clazz, PATH_ANNOTATION)
                ) {
            LOGGER.debug("Reload @Path annotated class {}, original classloader {}", clazz.getName(), original.getClassLoader());
            refresh(classLoader, 100);
        }
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.CREATE})
    public void newEntity(ClassLoader classLoader, CtClass clazz) throws Exception {
        if (AnnotationHelper.hasAnnotation(clazz, PATH_ANNOTATION)) {
            refresh(classLoader, 500);
        }
    }

    private void refresh(ClassLoader classLoader, int timeout) {
        if (!registeredDispatchers.isEmpty()) {
            try {
                Class<?> cmdClass = Class.forName(RefreshDispatchersCommand.class.getName(), true, appClassLoader);
                Command cmd = (Command) cmdClass.newInstance();
                ReflectionHelper.invoke(cmd, cmdClass, "setupCmd",
                        new Class[] {java.lang.ClassLoader.class, java.util.Set.class}, classLoader, registeredDispatchers);
                scheduler.scheduleCommand(cmd, timeout);
            } catch (Exception e) {
                LOGGER.error("refresh() exception {}.", e.getMessage());
            }
        }
    }

}
