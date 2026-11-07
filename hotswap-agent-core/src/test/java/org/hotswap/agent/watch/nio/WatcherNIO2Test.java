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

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.WatcherFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by bubnik on 2.11.13.
 */
public class WatcherNIO2Test {

    public static final String TESTDIR = "testdir";
    public static final String TEST_FILE = "testfile.txt";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    Watcher watcher;
    Path temp;

    @Before
    public void setup() throws IOException {
        watcher = new WatcherFactory().getWatcher();
        temp = Files.createTempDirectory("watcherNIO2Test");

        watcher.run();
    }

    @After
    public void tearDown() {
        watcher.run();
    }

    @Test
    public void createFile() throws IOException {
        final ResultHolder resultHolder = new ResultHolder();
        watcher.addEventListener(Thread.currentThread().getContextClassLoader(), temp.toUri(), new WatchEventListener() {
            @Override
            public void onEvent(WatchFileEvent event) {
                assertEquals("New file event type", FileEvent.CREATE, event.getEventType());
                assertTrue("File name", event.getURI().toString().endsWith("test.class"));
                resultHolder.result = true;
            }
        });

        File testFile = new File(temp.toFile(), "test.class");
        testFile.createNewFile();

        assertTrue("Event listener called", waitForResult(resultHolder));
    }

    @Test
    public void recreateDirWithFile() throws Exception {
        Set<FileEvent> events = ConcurrentHashMap.newKeySet();

        Path testDir = temp.resolve(TESTDIR);
        Files.createDirectories(testDir);
        File testFile = new File(testDir.toFile(), TEST_FILE);
        testFile.createNewFile();

        watcher.addEventListener(Thread.currentThread().getContextClassLoader(), temp.toUri(), event -> {
            if (event.getURI().toString().endsWith(TEST_FILE)) {
                events.add(event.getEventType());
            }
        });

        Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        Files.deleteIfExists(testDir);
        Files.createDirectories(testDir);
        testFile = new File(testDir.toFile(), TEST_FILE);
        testFile.createNewFile();

        // FSEvents on macOS reports a file that is recreated quickly as modified
        assertTrue("Event FileEvent.CREATE or MODIFY for " + TEST_FILE + " called",
                waitUntil(() -> events.contains(FileEvent.CREATE) || events.contains(FileEvent.MODIFY), 5));

        // let the remaining events of the recreation arrive
        Thread.sleep(1000);
        events.clear();
        Files.write(testFile.toPath(), "changed".getBytes());
        assertTrue("Event FileEvent.MODIFY for " + TEST_FILE + " in the recreated directory called",
                waitUntil(() -> events.contains(FileEvent.MODIFY), 5));
    }

    @Test
    public void recoverFromOverflow() throws Exception {
        // Do not start this watcher yet, so events pile up in the watch key.
        Watcher idleWatcher = new WatcherFactory().getWatcher();
        Path dir = tempFolder.newFolder().toPath();
        Set<String> created = ConcurrentHashMap.newKeySet();
        idleWatcher.addEventListener(Thread.currentThread().getContextClassLoader(), dir.toUri(), event -> {
            if (event.getEventType() == FileEvent.CREATE) {
                created.add(Paths.get(event.getURI()).getFileName().toString());
            }
        });

        // The JDK watch key holds at most 512 events before it reports OVERFLOW.
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String name = "file" + i + ".txt";
            Files.createFile(dir.resolve(name));
            expected.add(name);
        }
        Path subDir = Files.createDirectory(dir.resolve("sub"));
        Files.createFile(subDir.resolve("subfile.txt"));
        expected.add("subfile.txt");

        // Wait until the watch service queues the events. The polling watch service scans every 2 seconds.
        Thread.sleep(3000);
        try {
            idleWatcher.run();
            assertTrue("CREATE events for all files", waitUntil(() -> created.containsAll(expected), 10));

            Files.createFile(subDir.resolve("later.txt"));
            assertTrue("CREATE event for a file in a new sub directory", waitUntil(() -> created.contains("later.txt"), 10));
        } finally {
            idleWatcher.stop();
        }
    }

    @Test
    public void deliverEventsDuringRegistration() throws Exception {
        Path dir = tempFolder.newFolder().toPath();
        Set<String> created = ConcurrentHashMap.newKeySet();
        // A file created while the directories of a new listener are registered, e.g. by a build still running.
        WatcherNIO2 registeringWatcher = new WatcherNIO2() {
            @Override
            public void addDirectory(Path path) throws IOException {
                super.addDirectory(path);
                Files.createFile(path.resolve(TEST_FILE));
                waitUntil(() -> created.contains(TEST_FILE), 2);
            }
        };
        registeringWatcher.run();
        try {
            registeringWatcher.addEventListener(Thread.currentThread().getContextClassLoader(), dir.toUri(), event -> {
                if (event.getEventType() == FileEvent.CREATE) {
                    created.add(Paths.get(event.getURI()).getFileName().toString());
                }
            });
            assertTrue("CREATE event during registration", waitUntil(() -> created.contains(TEST_FILE), 5));
        } finally {
            registeringWatcher.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void registerModifiedDirectoryWithInvalidKey() throws Exception {
        Assume.assumeTrue(watcher instanceof WatcherNIO2);
        Path dir = tempFolder.newFolder().toPath();
        Path subDir = Files.createDirectory(dir.resolve(TESTDIR));
        Set<String> created = ConcurrentHashMap.newKeySet();
        watcher.addEventListener(Thread.currentThread().getContextClassLoader(), dir.toUri(), event -> {
            if (event.getEventType() == FileEvent.CREATE) {
                created.add(Paths.get(event.getURI()).getFileName().toString());
            }
        });
        WatcherNIO2 nio2Watcher = (WatcherNIO2) watcher;
        assertTrue(nio2Watcher.isWatched(subDir));

        // A polling watch service cancels the key of a directory polled while it is deleted, and reports
        // the directory as modified if it is created again before the parent directory is polled.
        Field dirKeysField = WatcherNIO2.class.getDeclaredField("dirKeys");
        dirKeysField.setAccessible(true);
        ((Map<Path, WatchKey>) dirKeysField.get(watcher)).get(subDir).cancel();
        Files.setLastModifiedTime(subDir, FileTime.fromMillis(System.currentTimeMillis() + 10000));

        assertTrue("directory registered again", waitUntil(() -> nio2Watcher.isWatched(subDir), 10));
        Files.createFile(subDir.resolve(TEST_FILE));
        assertTrue("CREATE event for a file in the directory", waitUntil(() -> created.contains(TEST_FILE), 10));
    }

    // ensure it works on file:/ URIs as returned by classloader
    //@Test
    public void testTargetClasses() throws Exception {
        URI uri = new URI("file:/" + temp);
        final ResultHolder resultHolder = new ResultHolder();
        watcher.addEventListener(Thread.currentThread().getContextClassLoader(), uri, new WatchEventListener() {
            @Override
            public void onEvent(WatchFileEvent event) {
                assertTrue("File name", event.getURI().toString().endsWith("test.class"));
                resultHolder.result = true;
            }
        });

        File testFile = new File(uri.toURL().getFile(), "test.class");
        testFile.createNewFile();

        assertTrue("Event listener not called", waitForResult(resultHolder));

        testFile.delete();
    }

    private boolean waitForResult(ResultHolder resultHolder) {
        return waitUntil(() -> resultHolder.result, 10);
    }

    // On Mac OS X, 10.9.4, this would fail for under 10 seconds, succeeded with 10 seconds.  I didn't look
    // into it further.
    private boolean waitUntil(BooleanSupplier condition, int secs) {
        for (int i = 0; i < 100 * secs; i++) {
            if (condition.getAsBoolean())
                return true;

            // waitForResult for NIO thread
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        return false;
    }

    private static class ResultHolder {
        boolean result = false;
    }
}
