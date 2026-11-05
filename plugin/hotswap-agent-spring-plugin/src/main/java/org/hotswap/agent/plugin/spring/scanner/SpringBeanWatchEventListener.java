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
package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.reload.SpringChangedReloadCommand;
import org.hotswap.agent.plugin.spring.reload.SpringReloadConfig;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Spring bean watch event listener.
 */
public class SpringBeanWatchEventListener implements WatchEventListener {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringBeanWatchEventListener.class);

    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait this this timeout after class file event.
     */
    private Scheduler scheduler;
    private ClassLoader appClassLoader;
    private String basePackage;

    // new class files that could not be read on CREATE, the content is written after the file is created
    private final Set<URI> notWrittenNewFiles = ConcurrentHashMap.newKeySet();

    public SpringBeanWatchEventListener(Scheduler scheduler, ClassLoader appClassLoader, String basePackage) {
        this.scheduler = scheduler;
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;
    }

    @Override
    public void onEvent(WatchFileEvent event) {
        if (!event.isFile() || !event.getURI().toString().endsWith(".class")) {
            return;
        }
        if (event.getEventType() == FileEvent.DELETE) {
            notWrittenNewFiles.remove(event.getURI());
            return;
        }
        // new File, or the content of a new file that was empty or incomplete on CREATE
        boolean newFile = event.getEventType() == FileEvent.CREATE;
        if (!newFile && !(event.getEventType() == FileEvent.MODIFY && notWrittenNewFiles.contains(event.getURI()))) {
            return;
        }
        // check that the class is not loaded by the classloader yet (avoid duplicate reload)
        String className;
        try {
            className = IOUtils.urlToClassName(event.getURI());
        } catch (IOException e) {
            LOGGER.trace("Watch event on resource '{}' skipped, the class file is not written completely yet " +
                    "(or a delete/create event sequence), waiting for modify event.", e, event.getURI());
            notWrittenNewFiles.add(event.getURI());
            return;
        }
        notWrittenNewFiles.remove(event.getURI());
        if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className)) {
            // refresh spring only for new classes
            LOGGER.trace("Scheduling Spring reload for class '{}' in classLoader {}", className, appClassLoader);
            scheduler.scheduleCommand(
                new SpringChangedReloadCommand(appClassLoader, new ClassPathBeanRefreshCommand(appClassLoader, basePackage, className, event, scheduler)),
                SpringReloadConfig.reloadDelayMillisForClass
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpringBeanWatchEventListener that = (SpringBeanWatchEventListener) o;
        return Objects.equals(appClassLoader, that.appClassLoader) && Objects.equals(basePackage, that.basePackage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appClassLoader, basePackage);
    }
}
