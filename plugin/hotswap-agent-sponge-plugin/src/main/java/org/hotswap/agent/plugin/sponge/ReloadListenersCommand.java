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

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ReloadListenersCommand implements Command {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadListenersCommand.class);

    private final ClassLoader appClassLoader;
    private final Set<Object> eventManagers;
    private final Map<Object, MethodHandles.Lookup> lookups;
    private final Set<Consumer<Class<?>>> callbacks;
    private final Class<?> listenerClass;

    ReloadListenersCommand(ClassLoader appClassLoader, Set<Object> eventManagers, Map<Object, MethodHandles.Lookup> lookups, Set<Consumer<Class<?>>> callbacks, Class<?> listenerClass) {
        this.appClassLoader = appClassLoader;
        this.eventManagers = eventManagers;
        this.lookups = lookups;
        this.callbacks = callbacks;
        this.listenerClass = listenerClass;
    }

    @Override
    public void executeCommand() {
        try {
            Class<?> spongeEventManagerClass = Class.forName("org.spongepowered.common.event.manager.SpongeEventManager", true, appClassLoader);
            Class<?> registeredListenerClass = Class.forName("org.spongepowered.common.event.manager.RegisteredListener", true, appClassLoader);
            Class<?> multiMapClass = Class.forName("com.google.common.collect.Multimap", true, appClassLoader);
            Class<?> pluginContainerClass = Class.forName("org.spongepowered.plugin.PluginContainer", true, appClassLoader);

            Field handlersByEventField = spongeEventManagerClass.getDeclaredField("handlersByEvent");
            handlersByEventField.setAccessible(true);

            Field lockField = spongeEventManagerClass.getDeclaredField("lock");
            lockField.setAccessible(true);

            Method unregisterListenersMethod = spongeEventManagerClass.getDeclaredMethod("unregisterListeners", Object.class);
            Method registerListenersMethod = spongeEventManagerClass.getDeclaredMethod("registerListeners", pluginContainerClass, Object.class);
            Method registerListenersLookupMethod = spongeEventManagerClass.getDeclaredMethod("registerListeners", pluginContainerClass, Object.class, MethodHandles.Lookup.class);

            Method valuesMethod = multiMapClass.getMethod("values");

            Method getHandleMethod = registeredListenerClass.getMethod("getHandle");
            Method getPluginMethod = registeredListenerClass.getMethod("getPlugin");

            HashMap<Object, HandleData> handles = new HashMap<>();
            synchronized (eventManagers) {
                for (Object eventManager : eventManagers) {
                    Object multiMap = handlersByEventField.get(eventManager);
                    synchronized (lockField.get(eventManager)) {
                        Collection<?> values = (Collection<?>) valuesMethod.invoke(multiMap);
                        for (Object listener : values) {
                            Object handle = getHandleMethod.invoke(listener);
                            if (!handle.getClass().equals(listenerClass)) {
                                continue;
                            }

                            Object plugin = getPluginMethod.invoke(listener);

                            handles.computeIfAbsent(handle, $ -> new HandleData(plugin))
                                    .eventManagers.add(eventManager);
                        }
                    }
                }
            }

            if (handles.isEmpty()) {
                return;
            }

            for (Map.Entry<Object, HandleData> entry : handles.entrySet()) {
                Object handle = entry.getKey();
                HandleData data = entry.getValue();
                MethodHandles.Lookup lookup = lookups.get(handle);
                for (Object eventManager : data.eventManagers) {
                    unregisterListenersMethod.invoke(eventManager, handle);
                    if (lookup == null) {
                        registerListenersMethod.invoke(eventManager, data.plugin, handle);
                    } else {
                        registerListenersLookupMethod.invoke(eventManager, data.plugin, handle, lookup);
                    }
                }
            }

            LOGGER.info("Successfully refreshed listeners for {}", listenerClass);

            for (Consumer<Class<?>> callback : callbacks) {
                try {
                    callback.accept(listenerClass);
                } catch (Throwable e) {
                    LOGGER.error("Listener update callback threw exception {}", e, listenerClass);
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Error refreshing listeners for {}", e, listenerClass);
        }
    }

    private static final class HandleData {

        private final Object plugin;
        private final Set<Object> eventManagers;

        private HandleData(Object plugin) {
            this.plugin = plugin;
            this.eventManagers = new HashSet<>();
        }
    }
}
