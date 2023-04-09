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
package org.hotswap.agent.plugin.log4j2;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

import java.net.URI;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Log4j2 configuration file reload.
 *
 * @author Lukasz Warzecha
 */
@Plugin(name = "Log4j2", description = "Log4j2 configuration reload.",
        testedVersions = { "2.1", "2.5", "2.7" })
public class Log4j2Plugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(Log4j2Plugin.class);

    @Init
    Watcher watcher;

    @Init
    ClassLoader appClassLoader;

    // ensure uri registered only once
    Set<URI> registeredURIs = new HashSet<>();

    volatile boolean initialized;

    /**
     * Callback method from
     * org.apache.logging.log4j.core.LoggerContext.setConfiguration(Configuration)
     *
     * @param config the Log4j2 configuration object
     */
    public void init(final Object config) {

        URI configURI = null;
        String url = null;

        try {
            Class<?> configurationClass = appClassLoader.loadClass("org.apache.logging.log4j.core.config.Configuration");
            Class<?> configurationSourceClass = appClassLoader.loadClass("org.apache.logging.log4j.core.config.ConfigurationSource");

            Object configurationSource = configurationClass.getDeclaredMethod("getConfigurationSource").invoke(config);
            url = (String) configurationSourceClass.getDeclaredMethod("getLocation").invoke(configurationSource);

            if (url == null) {
                LOGGER.warning("Location url is NULL on configurationSource={} - exiting.", configurationSource);
            } else {
                configURI = Paths.get(url).toUri();

                if (registeredURIs.contains(configURI)) {
                    return;
                }

                final URI parentUri = Paths.get(configURI).getParent().toUri();
                LOGGER.debug("Watching '{}' URI for Log4j2 configuration changes.", configURI);
                registeredURIs.add(configURI);
                watcher.addEventListener(appClassLoader, parentUri, new WatchEventListener() {

                    @Override
                    public void onEvent(WatchFileEvent event) {
                        if (event.getEventType() != FileEvent.DELETE && registeredURIs.contains(event.getURI())) {
                            reload(event.getURI());
                        }
                    }
                });
            }

            if (!initialized) {
                LOGGER.info("Log4j2 plugin initialized.");
                initialized = true;
            }
        } catch (java.nio.file.InvalidPathException e) {
            LOGGER.debug("Cannot convert {} to Path", url);
        } catch (Exception e) {
            LOGGER.error("Exception initializing Log4j2 on uri {}.", e, configURI);
        }
    }

    /**
     * Do the reload using Log4j2 configurator.
     *
     * @param uri the configuration file uri
     */
    protected void reload(URI uri) {

        try {
            IOUtils.toByteArray(uri);
        } catch (Exception e) {
            LOGGER.warning("Unable to open Log4j2 configuration file {}, is it deleted?", uri);
            return;
        }

        try {
            Class<?> logManagerClass = appClassLoader.loadClass("org.apache.logging.log4j.LogManager");
            Class<?> contextClass = appClassLoader.loadClass("org.apache.logging.log4j.core.LoggerContext");

            Object context = logManagerClass.getDeclaredMethod("getContext", Boolean.TYPE).invoke(logManagerClass,
                    true);

            // resetting configLocation forces reconfiguration
            contextClass.getDeclaredMethod("setConfigLocation", URI.class).invoke(context, uri);

            LOGGER.reload("Log4j2 configuration reloaded from uri '{}'.", uri);
        } catch (Exception e) {
            LOGGER.error("Unable to reload {} with Log4j2", e, uri);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.logging.log4j.core.LoggerContext")
    public static void registerConfigurator(ClassPool classPool, CtClass ctClass) throws NotFoundException,
            CannotCompileException {

        // fallback to the old version (<2.3) of Log4j2
        CtMethod m = ctClass.getDeclaredMethod("setConfiguration",
                new CtClass[] { classPool.get("org.apache.logging.log4j.core.config.Configuration") });

        m.insertAfter(PluginManagerInvoker.buildInitializePlugin(Log4j2Plugin.class));
        m.insertAfter(PluginManagerInvoker.buildCallPluginMethod(Log4j2Plugin.class, "init",
                "$1", "java.lang.Object"));

    }

}
