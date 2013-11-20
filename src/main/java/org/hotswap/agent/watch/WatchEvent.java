package org.hotswap.agent.watch;

import java.net.URI;

/**
 * An event on filesystem.
 *
 * @author Jiri Bubnik
 */
public interface WatchEvent {
    /**
     * Type of the event.
     */
    public enum WatchEventType {
        CREATE,
        MODIFY,
        DELETE
    }

    ;


    /**
     * @return type of the event
     */
    public WatchEventType getEventType();

    /**
     * URI to file or directory with the event
     *
     * @return URI
     */
    public URI getURI();

    /**
     * URI is a file.
     */
    public boolean isFile();

    /**
     * URI is a directory.
     */
    public boolean isDirectory();
}
