package org.hotswap.agent.watch;

/**
 * Listener to filesystem event change.
 *
 * @author Jiri Bubnik
 */
public interface WatchEventListener {
    /**
     * File/Directory is created/modified/deleted.
     *
     * @param event event type and file URI.
     */
    public void onEvent(WatchFileEvent event);
}
