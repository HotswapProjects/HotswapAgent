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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SpringBeanWatchEventListener implements WatchEventListener {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringBeanWatchEventListener.class);

    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait this this timeout after class file event.
     */
    private static final int WAIT_ON_CREATE = 600;

    private Scheduler scheduler;
    private ClassLoader appClassLoader;
    private String basePackage;

    public SpringBeanWatchEventListener(Scheduler scheduler, ClassLoader appClassLoader, String basePackage) {
        this.scheduler = scheduler;
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;
    }

    @Override
    public void onEvent(WatchFileEvent event) {
        // new File
        if (event.getEventType() == FileEvent.CREATE && event.isFile() && event.getURI().toString().endsWith(".class")) {
            // check that the class is not loaded by the classloader yet (avoid duplicate reload)
            String className;
            try {
                className = IOUtils.urlToClassName(event.getURI());
            } catch (IOException e) {
                LOGGER.trace("Watch event on resource '{}' skipped, probably Ok because of delete/create event " +
                        "sequence (compilation not finished yet).", e, event.getURI());
                return;
            }
            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className)) {
                // refresh spring only for new classes
                scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                        basePackage, className, event, scheduler), WAIT_ON_CREATE);
                LOGGER.trace("Scheduling Spring reload for class '{}' in classLoader {}", className, appClassLoader);
                scheduler.scheduleCommand(new SpringChangedReloadCommand(appClassLoader), SpringReloadConfig.reloadDelayMillis);
            }
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
