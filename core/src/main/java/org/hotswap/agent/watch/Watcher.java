package org.hotswap.agent.watch;

import java.net.URI;
import java.net.URL;

/**
 * Watch for change in directory directory tree.
 *
 * @author Jiri Bubnik
 */
public interface Watcher {
    /**
     * Register listeners on an event.
     *
     * @param classLoader the classloader to which this path is associated. May be null, but then this
     *                    listener will never be disassociated (even if application is undeployed)
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(ClassLoader classLoader, URI pathPrefix, WatchEventListener listener);

    /**
     * Register listeners on an event.
     *
     * @param classLoader the classloader to which this path is associated. May be null, but then this
     *                    listener will never be disassociated (even if application is undeployed)
     * @param pathPrefix where to listen
     * @param listener   the listener
     */
    void addEventListener(ClassLoader classLoader, URL pathPrefix, WatchEventListener listener);

    /**
     * Remove all listeners registered with a classloader
     * @param classLoader classloadr to close
     */
    void closeClassLoader(ClassLoader classLoader);


    /**
     * Run the watcher agent thread.
     */
    void run();

    /**
     * Stop the watcher agent thread.
     */
    void stop();
}
