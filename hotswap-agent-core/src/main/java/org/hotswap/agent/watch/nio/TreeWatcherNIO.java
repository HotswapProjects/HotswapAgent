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
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <p>
 * NIO2 watcher implementation for systems which support
 * ExtendedWatchEventModifier.FILE_TREE (Windows, and macOS with the native watch service of JBR)
 * </p>
 * <p>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * </p>
 *
 * @author alpapad@gmail.com
 */
public class TreeWatcherNIO extends AbstractNIO2Watcher {


    private final static WatchEvent.Modifier HIGH;
    private final static WatchEvent.Modifier FILE_TREE;
    private final static WatchEvent.Modifier[] MODIFIERS;

    static {
        // try to set high sensitivity
        HIGH =  getWatchEventModifier("com.sun.nio.file.SensitivityWatchEventModifier","HIGH");
        // try to set file tree modifier
        FILE_TREE = getWatchEventModifier("com.sun.nio.file.ExtendedWatchEventModifier", "FILE_TREE");

        MODIFIERS = Stream.of(FILE_TREE, HIGH).filter(Objects::nonNull).toArray(WatchEvent.Modifier[]::new);
    }

    /**
     * Check if the default watch service supports FILE_TREE.
     *
     * @return true if a directory tree can be watched with a single registration
     */
    public static boolean isSupported() {
        if (FILE_TREE == null) {
            return false;
        }
        // an empty directory, because JBR on macOS walks the whole registered tree
        Path dir = null;
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dir = Files.createTempDirectory("hotswap-watch-probe");
            dir.register(watchService, KINDS, MODIFIERS);
            return true;
        } catch (UnsupportedOperationException | IOException e) {
            return false;
        } finally {
            if (dir != null) {
                dir.toFile().delete();
            }
        }
    }
    /**
     * Instantiates a new tree watcher nio.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public TreeWatcherNIO() throws IOException {
        super();
    }

    /**
     * Register the given directory with the WatchService.
     *
     * @param dir the directory to register watch on
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void register(Path dir) throws IOException {

        for(Path p: keys.values()) {
            // This may NOT be correct for all cases (ensure resolve will work!)
            if(dir.startsWith(p)) {
                LOGGER.debug("Path {} watched via {}", dir, p);
                return;
            }
        }

        if (FILE_TREE == null) {
            LOGGER.debug("WATCHING:ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - high} {}", dir);
        } else {
            LOGGER.debug("WATCHING: ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree,high {}", dir);
        }

        final WatchKey key = dir.register(watcher, KINDS,  MODIFIERS);

        keys.put(key, dir);
    }

    /**
     * Register the given directory,  with the
     * WatchService. Sub-directories are automatically watched (filesystem supported)
     *
     * @param dir the dir
     * @param fromCreateEvent the true is called from event CREATE
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Override
    protected void registerAll(Path dir, boolean fromCreateEvent) throws IOException {
        LOGGER.info("Registering directory {} ", dir);
        register(dir);
    }

    @Override
    protected void rescan(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        dispatchRescanEvents(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warning("Unable to rescan directory {}", e, dir);
        }
    }
}