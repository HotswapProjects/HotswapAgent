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
package org.hotswap.agent.watch.nio;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.net.URI;
import java.nio.file.Files;
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
import java.nio.file.Path;
import java.nio.file.WatchEvent;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * Filesystem event.
 */
public class HotswapWatchFileEvent implements WatchFileEvent {

    private final WatchEvent<?> event;
    private final Path path;

    public HotswapWatchFileEvent(WatchEvent<?> event, Path path) {
        this.event = event;
        this.path = path;
    }

    @Override
    public FileEvent getEventType() {
        return toAgentEvent(event.kind());
    }

    @Override
    public URI getURI() {
        return path.toUri();
    }

    @Override
    public boolean isFile() {
        // return Files.isRegularFile(path); - did not work in some cases
        return !isDirectory();
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    @Override
    public String toString() {
        return "WatchFileEvent on path " + path + " for event " + event.kind();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HotswapWatchFileEvent that = (HotswapWatchFileEvent) o;

        if (!event.equals(that.event)) {
            return false;
        }
        if (!path.equals(that.path)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = event.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }



    // translate constants between NIO event and ageent event
    static FileEvent toAgentEvent(WatchEvent.Kind<?> kind) {
        if (kind == ENTRY_CREATE) {
            return FileEvent.CREATE;
        } else if (kind == ENTRY_MODIFY) {
            return FileEvent.MODIFY;
        } else if (kind == ENTRY_DELETE) {
            return FileEvent.DELETE;
        } else {
            throw new IllegalArgumentException("Unknown event type " + kind.name());
        }
    }
}