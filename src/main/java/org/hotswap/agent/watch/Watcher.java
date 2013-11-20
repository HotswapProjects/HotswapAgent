package org.hotswap.agent.watch;

import java.io.IOException;
import java.net.URI;

/**
 * Watch for change in directory.
 *
 * @author Jiri Bubnik
 */
public interface Watcher {
    /**
     * Add directory to watch.
     *
     * @param path URI to filesystem path.
     * @throws IOException eception in registration
     */
    void addDirectory(URI path) throws IOException;

    /**
     * Register listeners on event
     *
     * @param listener the listener
     */
    void addEventListener(URI pathPrefix, WatchEventListener listener);

    /**
     * Run the watcher agent thread.
     */
    void run();

    /**
     * Stop the watcher agent thread.
     */
    void stop();
}
