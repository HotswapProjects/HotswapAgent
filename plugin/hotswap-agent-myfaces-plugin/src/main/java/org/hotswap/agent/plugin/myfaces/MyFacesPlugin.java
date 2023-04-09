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
package org.hotswap.agent.plugin.myfaces;

import static org.hotswap.agent.plugin.myfaces.MyFacesConstants.MANAGED_BEAN_ANNOTATION;

import java.lang.reflect.Method;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.myfaces.command.ReloadManagedBeanCommand;
import org.hotswap.agent.plugin.myfaces.transformer.LifecycleImplTransformer;
import org.hotswap.agent.plugin.myfaces.transformer.ManagedBeanResolverTransformer;
import org.hotswap.agent.plugin.myfaces.transformer.MyFacesTransformer;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "MyFaces",
        description = "JSF/MyFaces. Clear resource bundle cache when *.properties files are changed.",
        testedVersions = {"2.2.10"},
        expectedVersions = {"2.2"},
        supportClass = { MyFacesTransformer.class, 
        		ManagedBeanResolverTransformer.class, 
        		LifecycleImplTransformer.class 
        		}
        )
public class MyFacesPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyFacesPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("MyFaces plugin initialized.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.config.RuntimeConfig")
    public static void facesApplicationAssociateInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String buildInitializePlugin = PluginManagerInvoker.buildInitializePlugin(MyFacesPlugin.class);

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(buildInitializePlugin);
        }
        LOGGER.debug("org.apache.myfaces.config.RuntimeConfig with plugin initialization.");
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properties")
    public void refreshJsfResourceBundles() {
        scheduler.scheduleCommand(refreshResourceBundles);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void reloadManagedBean(Class<?> beanClass) {
        if (!AnnotationHelper.hasAnnotation(beanClass, MANAGED_BEAN_ANNOTATION)) {
            return;
        }

        ReloadManagedBeanCommand command = new ReloadManagedBeanCommand(beanClass, appClassLoader);
        scheduler.scheduleCommand(command);
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = FileEvent.CREATE)
    public void registerManagedBean(CtClass beanCtClass) throws Exception {
        if (!AnnotationHelper.hasAnnotation(beanCtClass, MANAGED_BEAN_ANNOTATION)) {
            return;
        }

        ReloadManagedBeanCommand command = new ReloadManagedBeanCommand(beanCtClass, appClassLoader);
        scheduler.scheduleCommand(command);
    }

    private Command refreshResourceBundles = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing JSF resource bundles.");
            try {
                Class<?> clazz = resolveClass("java.util.ResourceBundle");
                Method clearCacheMethod = clazz.getDeclaredMethod("clearCache", ClassLoader.class);
                clearCacheMethod.invoke(null, appClassLoader);
            } catch (Exception e) {
                LOGGER.error("Error clear JSF resource bundles cache", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
