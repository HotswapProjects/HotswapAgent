/*
 * Copyright 2013-2025 the HotswapAgent authors.
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

import org.hotswap.agent.annotation.FileEvent;

import java.net.URI;

/**
 * An event on filesystem.
 *
 * @author Jiri Bubnik
 */
public interface WatchFileEvent {

    /**
     * @return type of the event
     */
    public FileEvent getEventType();

    /**
     * URI to file or directory with the event
     *
     * @return URI
     */
    public URI getURI();

    /**
     * URI is a file.
     */
    public boolean isFile();

    /**
     * URI is a directory.
     */
    public boolean isDirectory();
}
