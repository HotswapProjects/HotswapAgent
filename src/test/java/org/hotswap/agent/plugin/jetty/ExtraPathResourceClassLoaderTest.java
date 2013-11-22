package org.hotswap.agent.plugin.jetty;

import org.hotswap.agent.util.classloader.ExtraPathResourceClassLoader;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.nio.WatcherNIO2;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by bubnik on 12.11.13.
 */
public class ExtraPathResourceClassLoaderTest {

    @Test
    public void testGetResource() throws Exception {
        final Path directory = Files.createTempDirectory(ExtraPathResourceClassLoaderTest.class.getName());
        final File tempFile = new File(directory.toFile(), "test");
        tempFile.createNewFile();
        final Watcher watcher = new WatcherNIO2();
        watcher.run();

        ExtraPathResourceClassLoader classLoader = new ExtraPathResourceClassLoader();
        classLoader.init(new URL[]{directory.toUri().toURL()}, watcher);

        assertNull("Not returned before modification", classLoader.getResource(tempFile.getName()));

        // modify
        tempFile.setLastModified(new Date().getTime());
        for (int i = 0; i < 100; i++) {
            if (classLoader.getResource(tempFile.getName()) != null)
                break;

            // wait for NIO thread
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }


        assertNotNull("Returned after modification", classLoader.getResource(tempFile.getName()));
    }
}
