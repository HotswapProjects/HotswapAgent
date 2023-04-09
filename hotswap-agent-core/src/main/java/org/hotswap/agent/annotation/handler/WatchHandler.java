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
package org.hotswap.agent.annotation.handler;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * Watch method handler - handle @OnResourceFileEvent annotation on a method.
 *
 * @author Jiri Bubnik
 */
public class WatchHandler<T extends Annotation> implements PluginHandler<T> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatchHandler.class);

    protected PluginManager pluginManager;

    public WatchHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean initField(PluginAnnotation<T> pluginAnnotation) {
        throw new IllegalAccessError("@OnResourceFileEvent annotation not allowed on fields.");
    }


    @Override
    public boolean initMethod(final PluginAnnotation<T> pluginAnnotation) {
        LOGGER.debug("Init for method " + pluginAnnotation.getMethod());

        ClassLoader classLoader = pluginManager.getPluginRegistry().getAppClassLoader(pluginAnnotation.getPlugin());

        try {
            registerResources(pluginAnnotation, classLoader);
        } catch (IOException e) {
            LOGGER.error("Unable to register resources for annotation {} on method {} class {}", e,
                    pluginAnnotation.getAnnotation(),
                    pluginAnnotation.getMethod().getName(),
                    pluginAnnotation.getMethod().getDeclaringClass().getName());
            return false;
        }

        return true;
    }

    /**
     * Register resource change listener on URI:
     * - classpath (already should contain extraClasspath)
     * - plugin configuration - watchResources property
     */
    private void registerResources(final PluginAnnotation<T> pluginAnnotation, final ClassLoader classLoader) throws IOException {
        final T annot = pluginAnnotation.getAnnotation();
        WatchEventDTO watchEventDTO =  WatchEventDTO.parse(annot);

        String path = watchEventDTO.getPath();

        // normalize
        if (path == null || path.equals(".") || path.equals("/"))
            path = "";
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 2);


        // classpath resources (already should contain extraClasspath)
        Enumeration<URL> en = classLoader.getResources(path);
        while (en.hasMoreElements()) {
            try {
                URI uri = en.nextElement().toURI();

                // check that this is a local accessible file (not vfs inside JAR etc.)
                try {
                    new File(uri);
                } catch (Exception e) {
                    LOGGER.trace("Skipping uri {}, not a local file.", uri);
                    continue;
                }


                LOGGER.debug("Registering resource listener on classpath URI {}", uri);
                registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, uri);
            } catch (URISyntaxException e) {
                LOGGER.error("Unable convert root resource path URL to URI", e);
            }
        }

        // add extra directories for watchResources property
        if (!watchEventDTO.isClassFileEvent()) {
            for (URL url : pluginManager.getPluginConfiguration(classLoader).getWatchResources()) {
                try {
                    Path watchResourcePath = Paths.get(url.toURI());
                    Path pathInWatchResource = watchResourcePath.resolve(path);
                    if (pathInWatchResource.toFile().exists()) {
                        LOGGER.debug("Registering resource listener on watchResources URI {}", pathInWatchResource.toUri());
                        registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, pathInWatchResource.toUri());
                    }
                } catch (URISyntaxException e) {
                    LOGGER.error("Unable convert watch resource path URL {} to URI", e, url);
                }
            }
        }
    }

    /**
     * Using pluginManager.registerResourceListener() add new listener on URI.
     * <p/>
     * There might be several same events for a resource change (either from filesystem or when IDE clears and reloads
     * a class multiple time on rebuild). Use command scheduler to group same events into single invocation.
     */
    private void registerResourceListener(final PluginAnnotation<T> pluginAnnotation, final WatchEventDTO watchEventDTO,
                                          final ClassLoader classLoader, URI uri) throws IOException {
        pluginManager.getWatcher().addEventListener(classLoader, uri, new WatchEventListener() {
            @Override
            public void onEvent(WatchFileEvent event) {
                WatchEventCommand<T> command = WatchEventCommand.createCmdForEvent(pluginAnnotation, event, classLoader);
                if (command != null) {
                    pluginManager.getScheduler().scheduleCommand(command, watchEventDTO.getTimeout());
                    LOGGER.trace("Resource changed {}", event);
                }
            }
        });
    }


}
