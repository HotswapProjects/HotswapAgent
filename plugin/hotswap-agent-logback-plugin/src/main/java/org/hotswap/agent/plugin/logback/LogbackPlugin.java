/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.logback;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Logback configuration file reload.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Logback", description = "Logback configuration reload.",
        testedVersions = {"1.0.6"}
)
public class LogbackPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(LogbackPlugin.class);

    @Init
    Watcher watcher;

    @Init
    ClassLoader appClassLoader;

    // ensure uri registered only once
    Set<URI> registeredURIs = new HashSet<>();

    boolean initialized;

    /**
     * Callback method from ch.qos.logback.core.joran.GenericConfigurator.
     *
     * @param configurator the configurator object
     * @param url          configuration file url
     */
    public void initLogback(final Object configurator, final URL url) {
        try {
            final URI uri = url.toURI();

            // skip double registration on reload
            if (registeredURIs.contains(uri))
                return;

            LOGGER.debug("Watching '{}' URL for Logback configuration changes.", url);
            registeredURIs.add(uri);
            watcher.addEventListener(appClassLoader, uri, new WatchEventListener() {
                @Override
                public void onEvent(WatchFileEvent event) {
                    if (event.getEventType() != FileEvent.DELETE)
                        reload(configurator, url);
                }
            });
            if (!initialized) {
                LOGGER.info("Logback plugin initialized.");
                initialized = true;
            }
        } catch (Exception e) {
            LOGGER.error("Exception initializing logback configurator {} on url {}.", e, configurator, url);
        }
    }

    /**
     * Do the reload using logback configurator.
     *
     * @param configurator ch.qos.logback.core.joran.GenericConfigurator (GenericXMLConfigurator) instance
     * @param url          URL with configuration file
     */
    protected void reload(Object configurator, URL url) {

        try {
            IOUtils.toByteArray(url.toURI());
        } catch (Exception e) {
            LOGGER.warning("Unable to open logback configuration file {}, is it deleted?", url);
            return;
        }

        try {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (configurator) {
                ClassLoader classLoader = configurator.getClass().getClassLoader();

                Class<?> contextAwareBaseClass = classLoader.loadClass("ch.qos.logback.core.spi.ContextAwareBase");
                Class<?> contextInterface = classLoader.loadClass("ch.qos.logback.core.Context");
                Class<?> contextClass = classLoader.loadClass("ch.qos.logback.classic.LoggerContext");

                // a configurator can be used only once since Logback 1.3, so configure with a new one, as Logback
                // itself does on a configuration change
                Object context = contextAwareBaseClass.getDeclaredMethod("getContext").invoke(configurator);
                Object newConfigurator = newConfigurator(configurator);
                contextAwareBaseClass.getMethod("setContext", contextInterface).invoke(newConfigurator, context);

                // reset current context
                contextClass.getDeclaredMethod("reset").invoke(context);

                // configure the URL, doConfigure(URL) is public in GenericConfigurator (GenericXMLConfigurator since 1.3)
                newConfigurator.getClass().getMethod("doConfigure", URL.class).invoke(newConfigurator, url);

                LOGGER.reload("Logback configuration reloaded from url '{}'.", url);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to reload {} with logback configurator {}", e, url, configurator);
        }
    }

    /**
     * Create a new configurator of the same class. Constructor parameters (e.g. Spring Boot passes its
     * LoggingInitializationContext) are taken from the fields of the current configurator.
     */
    private static Object newConfigurator(Object configurator) throws Exception {
        Class<?> configuratorClass = configurator.getClass();
        Constructor<?>[] constructors = configuratorClass.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            boolean resolved = true;
            for (int i = 0; i < parameterTypes.length && resolved; i++) {
                args[i] = findFieldValue(configurator, parameterTypes[i]);
                resolved = args[i] != null;
            }
            if (resolved) {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        throw new IllegalStateException("Unable to create a new " + configuratorClass.getName());
    }

    private static Object findFieldValue(Object target, Class<?> type) throws IllegalAccessException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Transform configurator class to register logback config URL.
     * The class is GenericConfigurator in Logback 1.2 and older, and GenericXMLConfigurator since 1.3.
     */
    @OnClassLoadEvent(classNameRegexp = "ch.qos.logback.core.joran.(GenericConfigurator|GenericXMLConfigurator)")
    public static void registerConfigurator(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod m = ctClass.getDeclaredMethod("doConfigure", new CtClass[]{classPool.get("java.net.URL")});

        m.insertAfter(PluginManagerInvoker.buildInitializePlugin(LogbackPlugin.class));
        m.insertAfter(PluginManagerInvoker.buildCallPluginMethod(LogbackPlugin.class, "initLogback",
                "this", "java.lang.Object",
                "$1", "java.net.URL"));
    }

}
