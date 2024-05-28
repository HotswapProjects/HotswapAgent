/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.sponge;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Plugin(name = "Sponge",
        description = "Registers newly added listeners to existing listener class",
        testedVersions = { "11.0.0" },
        expectedVersions = { "11.0.0" })
public final class SpongePlugin {

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    private final Set<Object> eventManagers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Map<Object, MethodHandles.Lookup> lookups = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Consumer<Class<?>>> callbacks = Collections.synchronizedSet(new HashSet<>());

    @OnClassLoadEvent(classNameRegexp = "org.spongepowered.common.event.manager.SpongeEventManager")
    public static void registerManager(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder initialization = new StringBuilder("{");
        initialization.append(PluginManagerInvoker.buildInitializePlugin(SpongePlugin.class));
        initialization.append(PluginManagerInvoker.buildCallPluginMethod(SpongePlugin.class, "registerEventManager", "this", "java.lang.Object"));
        initialization.append("}");

        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(initialization.toString());

        StringBuilder captureHandleLookup = new StringBuilder("{");
        captureHandleLookup.append(PluginManagerInvoker.buildCallPluginMethod(SpongePlugin.class, "captureHandleLookup", "listenerObject", "java.lang.Object", "customLookup", "java.lang.invoke.MethodHandles$Lookup"));
        captureHandleLookup.append("}");

        ctClass.getDeclaredMethod("registerListener", new CtClass[] {
                classPool.get("org.spongepowered.plugin.PluginContainer"),
                classPool.get("java.lang.Object"),
                classPool.get("java.lang.invoke.MethodHandles$Lookup"),
        }).insertBefore(captureHandleLookup.toString());
    }

    public void registerEventManager(Object eventManager) {
        eventManagers.add(eventManager);
    }

    public void captureHandleLookup(Object handle, MethodHandles.Lookup lookup) {
        if (lookup == null) {
            return;
        }

        lookups.put(handle, lookup);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void reloadListeners(Class<?> clazz) {
        scheduler.scheduleCommand(new ReloadListenersCommand(appClassLoader, eventManagers, lookups, callbacks, clazz));
    }

    public void addCallback(Consumer<Class<?>> callback) {
        callbacks.add(callback);
    }
}
