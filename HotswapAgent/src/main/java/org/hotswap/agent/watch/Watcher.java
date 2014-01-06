package org.hotswap.agent.watch;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

/**
 * Watch for change in directory directory tree.
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
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(URI pathPrefix, WatchEventListener listener);

    /**
     * Register listeners on event
     *
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(URL pathPrefix, WatchEventListener listener);

    /**
     * Run the watcher agent thread.
     */
    void run();

    /**
     * Stop the watcher agent thread.
     */
    void stop();
}
