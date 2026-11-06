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
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.config.PluginConfiguration;
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
     * - classpath
     * - plugin configuration - extraClasspath, if it was added to the class loader (getResources() may not return
     *   it yet, e.g. the Tomcat plugin makes the webapp class loader return it after its plugins were initialized)
     * - plugin configuration - watchResources property
     * Each directory is registered only once.
     */
    private void registerResources(final PluginAnnotation<T> pluginAnnotation, final ClassLoader classLoader) throws IOException {
        final T annot = pluginAnnotation.getAnnotation();
        WatchEventDTO watchEventDTO =  WatchEventDTO.parse(annot);

        String path = watchEventDTO.getPath();

        // normalize
        if (path == null || path.equals("."))
            path = "";
        while (path.startsWith("/"))
            path = path.substring(1);
        while (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        PluginConfiguration pluginConfiguration = pluginManager.getPluginConfiguration(classLoader);


        Set<Path> registeredPaths = new HashSet<>();

        // classpath resources
        Enumeration<URL> en = classLoader.getResources(path);
        while (en.hasMoreElements()) {
            try {
                URI uri = en.nextElement().toURI();

                // check that this is a local accessible file (not vfs inside JAR etc.)
                File file;
                try {
                    file = new File(uri);
                } catch (Exception e) {
                    LOGGER.trace("Skipping uri {}, not a local file.", uri);
                    continue;
                }

                if (!registeredPaths.add(registrationKey(file.toPath()))) {
                    continue;
                }
                LOGGER.debug("Registering resource listener on classpath URI {}", uri);
                registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, uri);
            } catch (URISyntaxException e) {
                LOGGER.error("Unable convert root resource path URL to URI", e);
            }
        }

        // extraClasspath
        if (pluginConfiguration != null && pluginConfiguration.isExtraClasspathApplied()) {
            for (URL url : pluginConfiguration.getExtraClasspath()) {
                Path extraClasspathDir = toLocalPath(url);
                if (extraClasspathDir == null || !extraClasspathDir.toFile().isDirectory()) {
                    continue; // JAR or missing directory
                }
                Path pathInExtraClasspath = resolve(extraClasspathDir, path);
                if (pathInExtraClasspath != null && pathInExtraClasspath.toFile().exists()
                        && registeredPaths.add(registrationKey(pathInExtraClasspath))) {
                    LOGGER.debug("Registering resource listener on extraClasspath URI {}", pathInExtraClasspath.toUri());
                    registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, pathInExtraClasspath.toUri());
                }
            }
        }

        // add extra directories for watchResources property
        if (pluginConfiguration != null && !watchEventDTO.isClassFileEvent()) {
            for (URL url : pluginConfiguration.getWatchResources()) {
                Path watchResource = toLocalPath(url);
                Path pathInWatchResource = watchResource != null ? resolve(watchResource, path) : null;
                if (pathInWatchResource != null && pathInWatchResource.toFile().exists()
                        && registeredPaths.add(registrationKey(pathInWatchResource))) {
                    LOGGER.debug("Registering resource listener on watchResources URI {}", pathInWatchResource.toUri());
                    registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, pathInWatchResource.toUri());
                }
            }
        }
    }

    /**
     * Convert a configured URL to a path, or return null if the URL is not a local file.
     */
    private static Path toLocalPath(URL url) {
        if (!"file".equals(url.getProtocol())) {
            LOGGER.debug("Skipping URL {}, not a local file.", url);
            return null;
        }
        try {
            return Paths.get(url.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException e) {
            LOGGER.error("Unable convert URL {} to path", e, url);
            return null;
        }
    }

    /**
     * Resolve the watched path in a configured directory, or return null if it is not a valid path.
     */
    private static Path resolve(Path dir, String path) {
        try {
            return dir.resolve(path).normalize();
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unable to resolve path {} in {}", path, dir);
            return null;
        }
    }

    /**
     * The same directory may be reached by different paths (symbolic links, letter case on Windows).
     */
    private static Path registrationKey(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
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
