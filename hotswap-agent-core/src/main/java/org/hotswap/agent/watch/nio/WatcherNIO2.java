/*
 * Copyright 2013-2024 the HotswapAgent authors.
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

import org.hotswap.agent.watch.HotswapAgentWatchEvent;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * NIO2 watcher implementation.
 * <p/>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * <p/>
 * By http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */
public class WatcherNIO2 extends AbstractNIO2Watcher {
    private final static WatchEvent.Modifier HIGH;

    static {
        HIGH =  getWatchEventModifier("com.sun.nio.file.SensitivityWatchEventModifier","HIGH");
    }

    public WatcherNIO2() throws IOException {
        super();
    }

    @Override
    protected void registerAll(final Path dir, boolean fromCreateEvent) throws IOException {
        // register directory and sub-directories
        LOGGER.debug("Registering directory  {}", dir);

        Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (fromCreateEvent) {
                    sendFakeCreateEvents(dir);
                }
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void sendFakeCreateEvents(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String name = entry.getFileName().toString();
                    HotswapAgentWatchEvent<Path> ev = new HotswapAgentWatchEvent(ENTRY_CREATE, name);
                    LOGGER.debug("Fake watch event '{}' on '{}' --> {}", ev.kind().name(), entry, name);
                    dispatcher.add(ev, entry);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to send fake create events for directory {}", e, dir);
        }
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        // try to set high sensitivity
        final WatchKey key = HIGH == null ? dir.register(watcher, KINDS) : dir.register(watcher, KINDS, HIGH);
        keys.put(key, dir);
    }
}