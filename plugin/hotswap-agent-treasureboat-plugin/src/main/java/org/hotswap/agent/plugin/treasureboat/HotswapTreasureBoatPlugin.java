/*
 * Copyright 2013-2025 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.treasureboat;

import static org.hotswap.agent.annotation.LoadEvent.REDEFINE;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hotswap.agent.annotation.Init;
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
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "TreasureBoat", description = "Hotswap agent plugin for TreasureBoat app.",
testedVersions = "4.1.0",
expectedVersions = "4.1.0")

public class HotswapTreasureBoatPlugin {

    // Agent logger is a very simple custom logging mechanism. Do not use any common logging framework
    // to avoid compatibility and classloading issues.
    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapTreasureBoatPlugin.class);

    @Init
    Scheduler scheduler;

    private Method kvcDefaultImplementation_flushCaches;
    private Method kvcReflectionKeyBindingCreation_flushCaches;
    private Method kvcValueAccessor_flushCaches;
    private Method tbValidationDefaultImplementation_flushCaches;
    private Method tbApplication_removeComponentDefinitionCacheContents;
    private Object tbApplicationObject;
    private Method tbThreadsafeMutableDictionary_removeAllObjects;
    private Object actionClassesCacheDictionnary;

    private CtClass tbActionCtClass;
    private CtClass tbComponentCtClass;
    private CtClass tbValidationCtClass;

    @OnClassLoadEvent(classNameRegexp = "org.treasureboat.webcore.appserver.TBApplication")
    public static void threasureBoatIsStarting(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("run");
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(HotswapTreasureBoatPlugin.class));
        LOGGER.debug("TBApplication.run() enhanced with plugin initialization.");
    }


    // We use reflection to get the methods from TreasureBoat because the jar is not distributable publicly
    // and we want to build witout it.
    @Init
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void init(PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
        try {
            Class kvcDefaultImplementationClass = Class.forName("org.treasureboat.foundation.kv.TBFKeyValueCodingDefaultImplementation", false, appClassLoader);
            kvcDefaultImplementation_flushCaches = kvcDefaultImplementationClass.getMethod("_flushCaches");

            Class kvcReflectionKeyBindingCreationClass = Class.forName("org.treasureboat.foundation.kv.TBFKeyValueCoding$_ReflectionKeyBindingCreation", false, appClassLoader);
            kvcReflectionKeyBindingCreation_flushCaches = kvcReflectionKeyBindingCreationClass.getMethod("_flushCaches");

            Class kvcValueAccessorClass = Class.forName("org.treasureboat.foundation.kv.TBFKeyValueCoding$ValueAccessor", false, appClassLoader);
            kvcValueAccessor_flushCaches = kvcValueAccessorClass.getMethod("_flushCaches");

            Class tbValidationDefaultImplementationClass = Class.forName("org.treasureboat.foundation.validation.ITBFValidation$DefaultImplementation", false, appClassLoader);
            tbValidationDefaultImplementation_flushCaches = tbValidationDefaultImplementationClass.getMethod("_flushCaches");

            Class tbApplicationClass = Class.forName("org.treasureboat.webcore.appserver.TBApplication", false, appClassLoader);
            tbApplication_removeComponentDefinitionCacheContents = tbApplicationClass.getMethod("_removeComponentDefinitionCacheContents");
            tbApplicationObject = tbApplicationClass.getMethod("application").invoke(null);

            ClassPool classPool = ClassPool.getDefault();
            tbComponentCtClass = classPool.makeClass("org.treasureboat.webcore.components.TBComponent");
            tbValidationCtClass = classPool.makeClass("org.treasureboat.foundation.validation.ITBFValidation");
            tbActionCtClass = classPool.makeClass("org.treasureboat.webcore.appserver.TBWAbstractAction");

            Class tbActionClass = Class.forName("org.treasureboat.webcore.appserver.TBWAbstractAction", false, appClassLoader);
            Field actionClassesField = tbActionClass.getDeclaredField("_actionClasses");
            actionClassesField.setAccessible(true);
            actionClassesCacheDictionnary = actionClassesField.get(null);

            Class tbThreadsafeMutableDictionaryClass = Class.forName("org.treasureboat.foundation._private._TBFThreadsafeMutableDictionary", false, appClassLoader);
            tbApplication_removeComponentDefinitionCacheContents = tbApplicationClass.getMethod("_removeComponentDefinitionCacheContents");
            tbThreadsafeMutableDictionary_removeAllObjects = tbThreadsafeMutableDictionaryClass.getMethod("removeAllObjects");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = REDEFINE)
    public void reloadClass(CtClass ctClass) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, CannotCompileException {
        LOGGER.debug("Class "+ctClass.getSimpleName()+" redefined.");

        scheduler.scheduleCommand(clearKVCCacheCommand);
        scheduler.scheduleCommand(clearValidationCacheCommand);

        tbApplication_removeComponentDefinitionCacheContents.invoke(tbApplicationObject);
        if (ctClass.subclassOf(tbComponentCtClass)) {
            scheduler.scheduleCommand(clearComponentCacheCommand);
        }
        if (ctClass.subclassOf(tbActionCtClass)) {
            scheduler.scheduleCommand(clearActionCacheCommand);
        }
    }

     private ClearKVCCache clearKVCCacheCommand = new ClearKVCCache();
     public class ClearKVCCache implements Command {
        @Override
        public void executeCommand() {
            try {
                kvcDefaultImplementation_flushCaches.invoke(null);
                kvcReflectionKeyBindingCreation_flushCaches.invoke(null);
                kvcValueAccessor_flushCaches.invoke(null);
                LOGGER.info("Resetting KeyValueCoding caches");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

     private ClearComponentCache clearComponentCacheCommand = new ClearComponentCache();
     public class ClearComponentCache implements Command {
        @Override
        public void executeCommand() {
            try {
                tbApplication_removeComponentDefinitionCacheContents.invoke(tbApplicationObject);
                LOGGER.info("Resetting Component Definition cache");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

     private ClearActionCache clearActionCacheCommand = new ClearActionCache();
     public class ClearActionCache implements Command {
        @Override
        public void executeCommand() {
            try {
                tbThreadsafeMutableDictionary_removeAllObjects.invoke(actionClassesCacheDictionnary);
                LOGGER.info("Resetting Action class cache");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

     private ClearValidationCache clearValidationCacheCommand = new ClearValidationCache();
     public class ClearValidationCache implements Command {
        @Override
        public void executeCommand() {
            try {
                tbValidationDefaultImplementation_flushCaches.invoke(null);
                LOGGER.info("Resetting Validation Cache");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
