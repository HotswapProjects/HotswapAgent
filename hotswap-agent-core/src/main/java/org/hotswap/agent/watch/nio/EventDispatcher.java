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

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher.FileState;

/**
 * The EventDispatcher holds a queue of all events collected by the watcher but
 * not yet processed. It runs on its own thread and is responsible for calling
 * all the registered listeners.
 *
 * Since file system events can spawn too fast, this implementation works as
 * buffer for fast spawning events. The watcher is now responsible for
 * collecting and pushing events in this queue.
 *
 */
public class EventDispatcher implements Runnable {
    private static final int MAX_BATCH_SIZE = 1024;

    /** The logger. */
    protected AgentLogger LOGGER = AgentLogger.getLogger(this.getClass());

    /**
     * The Class Event.
     */
    static class Event {

        /** The event. */
        final WatchEvent<Path> event;

        /** The path. */
        final Path path;

        /**
         * Instantiates a new event.
         *
         * @param event
         *            the event
         * @param path
         *            the path
         */
        public Event(WatchEvent<Path> event, Path path) {
            super();
            this.event = event;
            this.path = path;
        }
    }

    static class EventKey {
        final WatchEvent.Kind<?> kind;
        final Path path;

        EventKey(WatchEvent.Kind<?> kind, Path path) {
            this.kind = kind;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventKey)) return false;
            EventKey eventKey = (EventKey) o;
            return kind == eventKey.kind && Objects.equals(path, eventKey.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, path);
        }
    }

    /** The map of listeners.  This is managed by the watcher service*/
    private final Map<Path, List<WatchEventListener>> listeners;

    /** The working queue. The event queue is drained and all pending events are added in this list */
    private final ArrayList<Event> working = new ArrayList<>();
    private final LinkedHashSet<EventKey> dedupSet = new LinkedHashSet<>();

    private volatile boolean skipUnchangedResources;

    /** The state of the files at their last modification event, used only by the dispatcher thread. */
    private final Map<Path, FileState> notifiedStates = new HashMap<>();

    /** The runnable. */
    private Thread runnable = null;

    /**
     * Instantiates a new event dispatcher.
     *
     * @param listeners
     *            the listeners
     */
    public EventDispatcher(Map<Path, List<WatchEventListener>> listeners) {
        super();
        this.listeners = listeners;
    }

    /** The event queue. */
    private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Block until first event is available and then drain burst of pending events.
                Event first = eventQueue.take();
                working.add(first);
                eventQueue.drainTo(working, MAX_BATCH_SIZE - 1);

                for (Event e : working) {
                    EventKey key = new EventKey(e.event.kind(), e.path);
                    if (dedupSet.add(key) && !isUnchanged(e)) {
                        callListeners(e.event, e.path);
                    }
                }
                dedupSet.clear();
                working.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Do not notify the listeners of a modification that changed only the attributes of a resource. It applies to
     * the resources of all applications in the JVM.
     */
    public void skipUnchangedResources() {
        skipUnchangedResources = true;
    }

    /**
     * Tells whether a modification changed only the attributes of the file, e.g. chmod: the watch service reports it
     * as ENTRY_MODIFY too. The first modification of a file, and the first one after the file was written, are always
     * passed on: there is no state to compare with, or it is not trusted yet. Class files are not checked, they have
     * autoHotswap.skipUnchangedClasses.
     */
    private boolean isUnchanged(Event e) {
        if (!skipUnchangedResources || e.path.toString().endsWith(".class")) {
            return false;
        }
        boolean modified = e.event.kind() == StandardWatchEventKinds.ENTRY_MODIFY;
        FileState state;
        try {
            state = modified ? FileState.read(e.path, null) : null;
        } catch (RuntimeException ex) {
            // must not stop the dispatcher thread
            LOGGER.debug("Unable to read the state of '{}'", ex, e.path);
            state = null;
        }
        if (state == null) {
            // created, deleted or not a regular file
            notifiedStates.remove(e.path);
            return false;
        }
        FileState previous = notifiedStates.put(e.path, state);
        if (previous == null || !state.hasContentOf(previous)) {
            return false;
        }
        LOGGER.debug("Watch event '{}' on '{}' skipped, only the attributes of the file changed",
                e.event.kind().name(), e.path);
        return true;
    }

    /**
     * Adds the.
     *
     * @param event
     *            the event
     * @param path
     *            the path
     */
    public void add(WatchEvent<Path> event, Path path) {
        eventQueue.offer(new Event(event, path));
    }

    /**
     * Call the listeners.
     * Listeners are organized per path in a Map. The number of paths is low so a simple iteration should be fast enough.
     *
     * @param event
     *            the event
     * @param path
     *            the path
     */
    // notify listeners about new event
    private void callListeners(final WatchEvent<?> event, final Path path) {
        boolean matchedOne = false;
        for (Map.Entry<Path, List<WatchEventListener>> list : listeners.entrySet()) {
            if (path.startsWith(list.getKey())) {
                matchedOne = true;
                for (WatchEventListener listener : new ArrayList<>(list.getValue())) {
                    WatchFileEvent agentEvent = new HotswapWatchFileEvent(event, path);
                    try {
                        listener.onEvent(agentEvent);
                    } catch (Throwable e) {
                        // LOGGER.error("Error in watch event '{}' listener
                        // '{}'", e, agentEvent, listener);
                    }
                }
            }
        }
        if (!matchedOne) {
            LOGGER.error("No match for  watch event '{}',  path '{}'", event, path);
        }
    }

    /**
     * Start.
     */
    public void start() {
        runnable = new Thread(this);
        runnable.setDaemon(true);
        runnable.setName("HotSwap Dispatcher");
        runnable.start();
    }

    /**
     * Stop.
     *
     * @throws InterruptedException
     *             the interrupted exception
     */
    public void stop() throws InterruptedException {
        if (runnable != null) {
            runnable.interrupt();
            runnable.join();
        }
        runnable = null;
    }
}
