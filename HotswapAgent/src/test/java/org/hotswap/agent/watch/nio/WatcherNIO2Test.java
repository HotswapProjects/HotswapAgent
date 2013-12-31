package org.hotswap.agent.watch.nio;

import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.WatcherFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by bubnik on 2.11.13.
 */
public class WatcherNIO2Test {

    Watcher watcher;
    Path temp;

    @Before
    public void setup() throws IOException {
        watcher = new WatcherFactory().getWatcher();
        temp = Files.createTempDirectory("watcherNIO2Test");

        watcher.addDirectory(temp.toUri());
        watcher.run();
    }

    @After
    public void tearDown() {
        watcher.run();
    }

    @Test
    public void createFile() throws IOException {
        final ResultHolder resultHolder = new ResultHolder();
        watcher.addEventListener(temp.toUri(), new WatchEventListener() {
            @Override
            public void onEvent(WatchEvent event) {
                assertEquals("New file event type", WatchEvent.WatchEventType.CREATE, event.getEventType());
                assertTrue("File name", event.getURI().toString().endsWith("test.class"));
                resultHolder.result = true;
            }
        });

        File testFile = new File(temp.toFile(), "test.class");
        testFile.createNewFile();

        assertTrue("Event listener called", waitForResult(resultHolder));
    }

    // ensure it works on file:/ URIs as returned by classloader
    //@Test
    public void testTargetClasses() throws Exception {
        URI uri = new URI("file:/J:/HotswapAgent/target/classes/");
        final ResultHolder resultHolder = new ResultHolder();
        watcher.addDirectory(uri);
        watcher.addEventListener(uri, new WatchEventListener() {
            @Override
            public void onEvent(WatchEvent event) {
                assertTrue("File name", event.getURI().toString().endsWith("test.class"));
                resultHolder.result = true;
            }
        });

        File testFile = new File(uri.toURL().getFile(), "test.class");
        testFile.createNewFile();

        assertTrue("Event listener not called", waitForResult(resultHolder));

        testFile.delete();
    }

    // each 10 ms check if result is true, max 1000 ms
    private boolean waitForResult(ResultHolder resultHolder) {
        for (int i = 0; i < 100; i++) {
            if (resultHolder.result)
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
