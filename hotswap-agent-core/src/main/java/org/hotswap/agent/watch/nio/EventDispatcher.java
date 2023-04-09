/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;

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

    /** The map of listeners.  This is managed by the watcher service*/
    private final Map<Path, List<WatchEventListener>> listeners;

    /** The working queue. The event queue is drained and all pending events are added in this list */
    private final ArrayList<Event> working = new ArrayList<>();

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
    private final ArrayBlockingQueue<Event> eventQueue = new ArrayBlockingQueue<>(500);

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        /*
         * The algorithm is naive:
         * a) work with not processed (in case);
         * b) drain the queue
         * c) work on newly collected
         * d) empty working queue
         */
        while (true) {
            // finish any pending ones
            for (Event e : working) {
                callListeners(e.event, e.path);
                if (Thread.interrupted()) {
                    return;
                }
                Thread.yield();
            }
            // drain the event queue
            eventQueue.drainTo(working);

            // work on new events.
            for (Event e : working) {
                callListeners(e.event, e.path);
                if (Thread.interrupted()) {
                    return;
                }
                Thread.yield();
            }

            // crear the working queue.
            working.clear();

            try {
                Thread.sleep(50);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                return;
            }
        }
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
