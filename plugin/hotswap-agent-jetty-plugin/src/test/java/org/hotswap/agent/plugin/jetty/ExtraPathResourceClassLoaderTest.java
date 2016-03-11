package org.hotswap.agent.plugin.jetty;

import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;
import org.hotswap.agent.util.test.WaitHelper;
import org.hotswap.agent.watch.Watcher;
import org.hotswap.agent.watch.nio.WatcherNIO2;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Check that ExtraPathResourceClassLoader classloader will load files only AFTER they are modified.
 * (support for extraClassPath configuration property.
 */
public class ExtraPathResourceClassLoaderTest {

    @Test
    public void testGetResource() throws Exception {
        // create arbitrary test file in a temp directory
        final Path directory = Files.createTempDirectory(ExtraPathResourceClassLoaderTest.class.getName());
        final File tempFile = new File(directory.toFile(), "test");
        tempFile.createNewFile();

        // instantiate the watcher service
        final Watcher watcher = new WatcherNIO2();
        watcher.run();

        // special classloader to load modified files from the temp directory
        final WatchResourcesClassLoader classLoader = new WatchResourcesClassLoader();
        classLoader.initWatchResources(new URL[]{directory.toUri().toURL()}, watcher);

        assertNull("Not returned before modification", classLoader.getResource(tempFile.getName()));

        // modify the tested file, make sure we change second for Mac file system.
        tempFile.setLastModified(new Date().getTime()+1000);
		        
        // check that classloader will return the modified file
        WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return (classLoader.getResource(tempFile.getName()) != null);
            }
        }, 5000);


        // final check to fail the test if no event recieved until timeout.
        assertNotNull("Returned after modification", classLoader.getResource(tempFile.getName()));
    }
}
