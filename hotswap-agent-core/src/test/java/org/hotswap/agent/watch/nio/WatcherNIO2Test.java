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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
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
    public void recreateDirWithFile() throws IOException {
        final ResultHolder resultHolder = new ResultHolder();

        Path testDir = temp.resolve(TESTDIR);
        Files.createDirectories(testDir);
        File testFile = new File(testDir.toFile(), TEST_FILE);
        testFile.createNewFile();

        watcher.addEventListener(Thread.currentThread().getContextClassLoader(), temp.toUri(), new WatchEventListener() {
            @Override
            public void onEvent(WatchFileEvent event) {
                if (event.getEventType() == FileEvent.CREATE && event.getURI().toString().endsWith(TEST_FILE)) {
                    resultHolder.result = true;
                }
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

        assertTrue("Event FileEvent.CREATE for " + TEST_FILE + "  called", waitForResult(resultHolder, 5));
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
        return waitForResult(resultHolder, 10);
    }

    private boolean waitForResult(ResultHolder resultHolder, int secs) {
        return waitUntil(() -> resultHolder.result, secs);
    }

    // On Mac OS X, 10.9.4, this would fail for under 10 seconds, succeeded with 10 seconds.  I didn't look
    // into it further.
    // each 10 ms check if result is true, max 10000 ms
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
