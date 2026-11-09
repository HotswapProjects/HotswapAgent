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
package org.hotswap.agent.watch.nio;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
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

    private final Map<Path, WatchKey> dirKeys = new ConcurrentHashMap<>();

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
                // Register before listing the files. Otherwise files created in between get no event.
                register(dir);
                if (fromCreateEvent) {
                    sendFakeCreateEvents(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void sendFakeCreateEvents(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    dispatchFakeEvent(ENTRY_CREATE, entry);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to send fake create events for directory {}", e, dir);
        }
    }

    @Override
    protected void rescan(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry, NOFOLLOW_LINKS)) {
                    // The CREATE event of a new directory may be dropped too.
                    // Do not register watched ones again, as some JDKs reset the watch and lose events.
                    if (!isWatched(entry)) {
                        registerCreatedDirectory(entry);
                    }
                } else if (Files.isRegularFile(entry)) {
                    dispatchRescanEvents(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to rescan directory {}", e, dir);
        }
    }

    @Override
    protected void registerIfNotWatched(Path dir) {
        if (!isWatched(dir)) {
            registerCreatedDirectory(dir);
        }
    }

    private boolean isWatched(Path dir) {
        WatchKey key = dirKeys.get(dir);
        return key != null && key.isValid();
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        // Each classloader with the directory on its classpath registers it. Some watch services, such as the
        // native one of the JetBrains Runtime on macOS, reset a watch registered again and lose a change made
        // just before, so a watched directory keeps the key it has.
        if (isWatched(dir)) {
            return;
        }
        // try to set high sensitivity
        final WatchKey key = HIGH == null ? dir.register(watcher, KINDS) : dir.register(watcher, KINDS, HIGH);
        keys.put(key, dir);
        dirKeys.put(dir, key);
    }
}
