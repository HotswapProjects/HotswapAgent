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
package org.hotswap.agent.watch;

import java.net.URI;
import java.net.URL;

/**
 * Watch for change in directory directory tree.
 *
 * @author Jiri Bubnik
 */
public interface Watcher {
    /**
     * Register listeners on an event.
     *
     * @param classLoader the classloader to which this path is associated. May be null, but then this
     *                    listener will never be disassociated (even if application is undeployed)
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(ClassLoader classLoader, URI pathPrefix, WatchEventListener listener);

    /**
     * Register listeners on an event.
     *
     * @param classLoader the classloader to which this path is associated. May be null, but then this
     *                    listener will never be disassociated (even if application is undeployed)
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(ClassLoader classLoader, URL pathPrefix, WatchEventListener listener);

    /**
     * Remove all listeners registered with a classloader
     * @param classLoader classloadr to close
     */
    void closeClassLoader(ClassLoader classLoader);


    /**
     * Run the watcher agent thread.
     */
    void run();

    /**
     * Stop the watcher agent thread.
     */
    void stop();
}
