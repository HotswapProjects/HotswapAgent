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

import org.hotswap.agent.watch.nio.AbstractNIO2Watcher.FileState;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher.Snapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The state of the files of a watch key that is kept between two overflows.
 */
public class SnapshotTest {

    private final Path root = Paths.get("watched");
    private final Snapshot snapshot = new Snapshot(root);

    @Test
    public void removesDeletedFile() {
        put("a.txt", "dir/b.txt");

        snapshot.remove(root.resolve("dir/b.txt"));

        assertNull(snapshot.get(root.resolve("dir/b.txt")));
        assertSame(FileState.DIRTY, snapshot.get(root.resolve("a.txt")));
        assertEquals(files("a.txt"), files());
    }

    @Test
    public void removesFilesUnderDeletedDirectory() {
        // a directory moved away from a FILE_TREE key gets one DELETE event, its files get none
        put("a.txt", "dir/b.txt", "dir/sub/c.txt", "dir/sub/deep/d.txt", "dir-other/e.txt", "other/dir/f.txt");

        snapshot.remove(root.resolve("dir"));

        assertEquals(files("a.txt", "dir-other/e.txt", "other/dir/f.txt"), files());
    }

    @Test
    public void removesFilesOfDirectoryCreatedAgain() {
        put("dir/sub/a.txt");
        snapshot.remove(root.resolve("dir/sub"));
        put("dir/sub/b.txt");

        snapshot.remove(root.resolve("dir"));

        assertEquals(new HashSet<Path>(), files());
    }

    @Test
    public void ignoresDeletedPathThatIsNotKnown() {
        put("a.txt");

        snapshot.remove(root.resolve("temp.txt"));
        snapshot.remove(root.resolve("unknown/temp.txt"));

        assertEquals(files("a.txt"), files());
    }

    private void put(String... files) {
        for (String file : files) {
            snapshot.put(root.resolve(file), FileState.DIRTY);
        }
    }

    private Set<Path> files(String... files) {
        Set<Path> paths = new HashSet<>();
        for (String file : files) {
            paths.add(root.resolve(file));
        }
        return paths;
    }

    private Set<Path> files() {
        Set<Path> paths = new HashSet<>();
        snapshot.forEachFile(paths::add);
        return paths;
    }
}
