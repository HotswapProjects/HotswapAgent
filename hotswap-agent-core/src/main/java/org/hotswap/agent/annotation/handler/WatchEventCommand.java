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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * Command to schedule after resource change.
 * <p/>
 * Equals is declared on all command params to group same change events to a single onWatchEvent. For event
 * only the URI is compared to group multiple event types.
 */
public class WatchEventCommand<T extends Annotation> extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WatchEventCommand.class);

    private final PluginAnnotation<T> pluginAnnotation;
    private final WatchEventDTO watchEventDTO;
    private final WatchFileEvent event;
    private final ClassLoader classLoader;

    public static <T extends Annotation> WatchEventCommand<T> createCmdForEvent(PluginAnnotation<T> pluginAnnotation,
            WatchFileEvent event, ClassLoader classLoader) {
        WatchEventDTO watchEventDTO = WatchEventDTO.parse(pluginAnnotation.getAnnotation());

        // Watch event is not supported.
        if (!watchEventDTO.accept(event)) {
            return null;
        }

        // regular files filter
        if (watchEventDTO.isOnlyRegularFiles() && !event.isFile()) {
            LOGGER.trace("Skipping URI {} because it is not a regular file.", event.getURI());
            return null;
        }

        // watch type filter
        if (!Arrays.asList(watchEventDTO.getEvents()).contains(event.getEventType())) {
            LOGGER.trace("Skipping URI {} because it is not a requested event.", event.getURI());
            return null;
        }

        // resource name filter regexp
        if (watchEventDTO.getFilter() != null && watchEventDTO.getFilter().length() > 0) {
            if (!event.getURI().toString().matches(watchEventDTO.getFilter())) {
                LOGGER.trace("Skipping URI {} because it does not match filter.", event.getURI(), watchEventDTO.getFilter());
                return null;
            }
        }
        return new WatchEventCommand<>(pluginAnnotation, event, classLoader, watchEventDTO);
    }

    private WatchEventCommand(PluginAnnotation<T> pluginAnnotation, WatchFileEvent event, ClassLoader classLoader, WatchEventDTO watchEventDTO) {
        this.pluginAnnotation = pluginAnnotation;
        this.event = event;
        this.classLoader = classLoader;
        this.watchEventDTO = watchEventDTO;
    }

    @Override
    public void executeCommand() {
        LOGGER.trace("Executing for pluginAnnotation={}, event={} at classloader {}", pluginAnnotation, event, classLoader);
        onWatchEvent(pluginAnnotation, event, classLoader);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WatchEventCommand that = (WatchEventCommand) o;

        if (classLoader != null ? !classLoader.equals(that.classLoader) : that.classLoader != null) return false;
        if (event != null ? !event.equals(that.event) : that.event != null) return false;
        if (pluginAnnotation != null ? !pluginAnnotation.equals(that.pluginAnnotation) : that.pluginAnnotation != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = pluginAnnotation != null ? pluginAnnotation.hashCode() : 0;
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (classLoader != null ? classLoader.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WatchEventCommand{" +
                "pluginAnnotation=" + pluginAnnotation +
                ", event=" + event +
                ", classLoader=" + classLoader +
                '}';
    }

    /**
     * Run plugin the method.
     */
    public void onWatchEvent(PluginAnnotation<T> pluginAnnotation, WatchFileEvent event, ClassLoader classLoader) {
        final T annot = pluginAnnotation.getAnnotation();
        Object plugin = pluginAnnotation.getPlugin();

        //we may need to crate CtClass on behalf of the client and close it after invocation.
        CtClass ctClass = null;

        // class file regexp
        if (watchEventDTO.isClassFileEvent()) {
            try {
                // TODO creating class only to check name may slow down if lot of handlers is in use.
                ctClass = createCtClass(event.getURI(), classLoader);
            } catch (Exception e) {
                LOGGER.error("Unable create CtClass for URI '{}'.", e, event.getURI());
                return;
            }

            // unable to create CtClass or it's name does not match
            if (ctClass == null || !ctClass.getName().matches(watchEventDTO.getClassNameRegexp()))
                return;
        }

        LOGGER.debug("Executing resource changed method {} on class {} for event {}",
                pluginAnnotation.getMethod().getName(), plugin.getClass().getName(), event);


        List<Object> args = new ArrayList<>();
        for (Class<?> type : pluginAnnotation.getMethod().getParameterTypes()) {
            if (type.isAssignableFrom(ClassLoader.class)) {
                args.add(classLoader);
            } else if (type.isAssignableFrom(URI.class)) {
                args.add(event.getURI());
            } else if (type.isAssignableFrom(URL.class)) {
                try {
                    args.add(event.getURI().toURL());
                } catch (MalformedURLException e) {
                    LOGGER.error("Unable to convert URI '{}' to URL.", e, event.getURI());
                    return;
                }
            } else if (type.isAssignableFrom(ClassPool.class)) {
                args.add(ClassPool.getDefault());
            } else if (type.isAssignableFrom(FileEvent.class)) {
                args.add(event.getEventType());
            } else if (watchEventDTO.isClassFileEvent() && type.isAssignableFrom(CtClass.class)) {
                args.add(ctClass);
            } else if (watchEventDTO.isClassFileEvent() && type.isAssignableFrom(String.class)) {
                args.add(ctClass != null ? ctClass.getName() : null);
            } else {
                LOGGER.error("Unable to call method {} on plugin {}. Method parameter type {} is not recognized.",
                        pluginAnnotation.getMethod().getName(), plugin.getClass().getName(), type);
                return;
            }
        }
        try {
            pluginAnnotation.getMethod().invoke(plugin, args.toArray());

            // close CtClass if created from here
            if (ctClass != null) {
                ctClass.detach();
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("IllegalAccessException in method '{}' class '{}' classLoader '{}' on plugin '{}'",
                e, pluginAnnotation.getMethod().getName(), ctClass != null ? ctClass.getName() : "",
                classLoader != null ? classLoader.getClass().getName() : "", plugin.getClass().getName());
        } catch (InvocationTargetException e) {
            LOGGER.error("InvocationTargetException in method '{}' class '{}' classLoader '{}' on plugin '{}'",
                e, pluginAnnotation.getMethod().getName(), ctClass != null ? ctClass.getName() : "",
                classLoader != null ? classLoader.getClass().getName() : "", plugin.getClass().getName());
        }
    }


    /**
     * Creats javaassist CtClass for bytecode manipulation. Add default classloader.
     *
     * @param uri         uri
     * @param classLoader loader
     * @return created class
     * @throws org.hotswap.agent.javassist.NotFoundException
     */
    private CtClass createCtClass(URI uri, ClassLoader classLoader) throws NotFoundException, IOException {
        File file = new File(uri);
        if (file.exists()) {
          ClassPool cp = new ClassPool();
          cp.appendClassPath(new LoaderClassPath(classLoader));
          return cp.makeClass(new ByteArrayInputStream(IOUtils.toByteArray(uri)));
        }
        return null;
    }
}
