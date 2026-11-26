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

import org.hotswap.agent.watch.HotswapAgentWatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventDispatcherTest {
    private static final String MARKER = "marker";
    private static final FileTime OLD = FileTime.from(1_700_000_000L, TimeUnit.SECONDS);
    private static final FileTime OLDER = FileTime.from(1_600_000_000L, TimeUnit.SECONDS);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path root;
    private EventDispatcher dispatcher;
    // the events the listener was notified of, e.g. "MODIFY application.properties"
    private final List<String> notified = Collections.synchronizedList(new ArrayList<String>());
    private final BlockingQueue<String> markers = new LinkedBlockingQueue<>();
    private int markerCount;

    @Before
    public void setup() {
        root = folder.getRoot().toPath();
        Map<Path, List<WatchEventListener>> listeners = new ConcurrentHashMap<>();
        listeners.put(root, Collections.<WatchEventListener>singletonList(event -> {
            String name = Paths.get(event.getURI()).getFileName().toString();
            if (name.startsWith(MARKER)) {
                markers.add(name);
            } else {
                notified.add(event.getEventType() + " " + name);
            }
        }));
        dispatcher = new EventDispatcher(listeners);
        dispatcher.start();
    }

    @After
    public void tearDown() throws InterruptedException {
        dispatcher.stop();
    }

    @Test
    public void modificationOfAttributesOnlyIsSkipped() throws Exception {
        Path properties = write("application.properties", "a=1", OLDER);
        dispatcher.skipUnchangedResources();

        dispatch(ENTRY_MODIFY, properties);
        assertTrue(properties.toFile().setWritable(false));
        dispatch(ENTRY_MODIFY, properties);
        assertTrue(properties.toFile().setWritable(true));
        dispatch(ENTRY_MODIFY, properties);

        assertEquals(Collections.singletonList("MODIFY application.properties"), notified);
    }

    @Test
    public void writtenFileIsNotified() throws Exception {
        Path properties = write("application.properties", "a=1", OLDER);
        dispatcher.skipUnchangedResources();
        dispatch(ENTRY_MODIFY, properties);

        // another modification time with the same size
        write("application.properties", "a=2", OLD);
        dispatch(ENTRY_MODIFY, properties);
        // another size with the same modification time
        write("application.properties", "a=22", OLD);
        dispatch(ENTRY_MODIFY, properties);
        dispatch(ENTRY_MODIFY, properties);

        assertEquals(Arrays.asList("MODIFY application.properties", "MODIFY application.properties",
                "MODIFY application.properties"), notified);
    }

    @Test
    public void classFileIsAlwaysNotified() throws Exception {
        Path classFile = write("Application.class", "a=1", OLDER);
        dispatcher.skipUnchangedResources();

        dispatch(ENTRY_MODIFY, classFile);
        dispatch(ENTRY_MODIFY, classFile);

        assertEquals(Arrays.asList("MODIFY Application.class", "MODIFY Application.class"), notified);
    }

    /**
     * The file may be written again within the granularity of its modification time and keep its size.
     */
    @Test
    public void fileModifiedRecentlyIsNotTrusted() throws Exception {
        Path properties = write("application.properties", "a=1",
                FileTime.fromMillis(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)));
        dispatcher.skipUnchangedResources();

        dispatch(ENTRY_MODIFY, properties);
        dispatch(ENTRY_MODIFY, properties);

        assertEquals(Arrays.asList("MODIFY application.properties", "MODIFY application.properties"), notified);
    }

    @Test
    public void createdOrDeletedFileIsNotifiedAgain() throws Exception {
        Path properties = write("application.properties", "a=1", OLDER);
        dispatcher.skipUnchangedResources();

        dispatch(ENTRY_MODIFY, properties);
        dispatch(ENTRY_CREATE, properties);
        dispatch(ENTRY_MODIFY, properties);
        dispatch(ENTRY_DELETE, properties);
        dispatch(ENTRY_MODIFY, properties);
        dispatch(ENTRY_MODIFY, properties);

        assertEquals(Arrays.asList("MODIFY application.properties", "CREATE application.properties",
                "MODIFY application.properties", "DELETE application.properties", "MODIFY application.properties"),
                notified);
    }

    private Path write(String name, String content, FileTime modified) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, modified);
        return file;
    }

    /**
     * Dispatches the event and waits until it is handled: the dispatcher drops the duplicates of a batch.
     */
    private void dispatch(WatchEvent.Kind<Path> kind, Path file) throws InterruptedException {
        dispatcher.add(new HotswapAgentWatchEvent<>(kind, file.getFileName()), file);
        Path marker = root.resolve(MARKER + markerCount++);
        dispatcher.add(new HotswapAgentWatchEvent<>(ENTRY_CREATE, marker.getFileName()), marker);
        assertEquals(marker.getFileName().toString(), markers.poll(10, TimeUnit.SECONDS));
    }
}
