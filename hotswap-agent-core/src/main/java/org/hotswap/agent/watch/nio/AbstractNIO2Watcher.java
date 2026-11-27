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

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.watch.HotswapAgentWatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

/**
 * NIO2 watcher implementation for systems which support
 * ExtendedWatchEventModifier.FILE_TREE
 * <p/>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * <p/>
 * By http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */
public abstract class AbstractNIO2Watcher implements Watcher {
    protected AgentLogger LOGGER = AgentLogger.getLogger(this.getClass());

    protected final static WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY };

    private static final boolean UNIX_VIEW = FileSystems.getDefault().supportedFileAttributeViews().contains("unix");

    // replaced by closeClassLoader() while the runner thread polls it
    protected volatile WatchService watcher;
    protected final Map<WatchKey, Path> keys;
    // State of the files of each key that has overflowed, the next overflow sends events only for changed files.
    // It is kept as long as the key, and it is built again by each rescan. It takes memory for each file of the
    // key, with TreeWatcherNIO for each file of the watched tree.
    private final Map<WatchKey, Snapshot> snapshots = new ConcurrentHashMap<>();
    // number of overflows, for tests
    volatile int overflowCount;
    private final Map<Path, List<WatchEventListener>> listeners = new ConcurrentHashMap<>();

    // keep track about which classloader requested which event
    protected Map<WatchEventListener, ClassLoader> classLoaderListeners = new ConcurrentHashMap<>();

    private Thread runner;

    private volatile boolean stopped;

    protected final EventDispatcher dispatcher;

    public AbstractNIO2Watcher() throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new ConcurrentHashMap<>();
        dispatcher = new EventDispatcher(listeners);
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    @Override
    public synchronized void addEventListener(ClassLoader classLoader, URI pathPrefix, WatchEventListener listener) {
        File path;
        try {
            // check that it is regular file
            // toString() is weird and solves HiarchicalUriException for URI
            // like "file:./src/resources/file.txt".
            path = new File(pathPrefix);
         } catch (IllegalArgumentException e) {
            if (!LOGGER.isLevelEnabled(Level.TRACE)) {
                LOGGER.warning("Unable to watch for path {}, not a local regular file or directory.", pathPrefix);
            } else {
                LOGGER.trace("Unable to watch for path {} exception", e, pathPrefix);
            }
            return;
        }

        // Add the listener first, events from the directories registered below can be dispatched right away.
        Path listenerPath = Paths.get(pathPrefix);
        List<WatchEventListener> list = listeners.computeIfAbsent(listenerPath, k -> new ArrayList<>());
        boolean added = !list.contains(listener);
        if (added) {
            list.add(listener);
        }

        try {
            addDirectory(path.toPath());
        } catch (IOException e) {
            if (added) {
                list.remove(listener);
                if (list.isEmpty()) {
                    listeners.remove(listenerPath, list);
                }
            }
            if (!LOGGER.isLevelEnabled(Level.TRACE)) {
                LOGGER.warning("Unable to watch for path {}, not a local regular file or directory.", pathPrefix);
            } else {
                LOGGER.trace("Unable to watch path with prefix '{}' for changes.", e, pathPrefix);
            }
            return;
        }

        if (classLoader != null) {
            classLoaderListeners.put(listener, classLoader);
        }
    }

    @Override
    public void skipUnchangedResources() {
        dispatcher.skipUnchangedResources();
    }

    @Override
    public void addEventListener(ClassLoader classLoader, URL pathPrefix, WatchEventListener listener) {
        if (pathPrefix == null) {
            return;
        }

        try {
            addEventListener(classLoader, pathPrefix.toURI(), listener);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to convert URL to URI " + pathPrefix, e);
        }
    }

    /**
     * Remove all transformers registered with a classloader
     *
     * @param classLoader
     */
    @Override
    public void closeClassLoader(ClassLoader classLoader) {
        for (Iterator<Entry<WatchEventListener, ClassLoader>> entryIterator = classLoaderListeners.entrySet().iterator(); entryIterator.hasNext();) {
            Entry<WatchEventListener, ClassLoader> entry = entryIterator.next();
            if (entry.getValue().equals(classLoader)) {
                entryIterator.remove();
                try {
                    for (Iterator<Entry<Path, List<WatchEventListener>>> listenersIterator = listeners.entrySet().iterator(); listenersIterator.hasNext();) {
                        Entry<Path, List<WatchEventListener>> pathListenerEntry = listenersIterator.next();
                        List<WatchEventListener> l = pathListenerEntry.getValue();

                        if (l != null) {
                            l.remove(entry.getKey());
                        }

                        if (l == null || l.isEmpty()) {
                            listenersIterator.remove();
                        }

                    }
                } catch (Exception e) {
                    LOGGER.error("Ooops", e);
                }
            }
        }
        // cleanup...
        if (classLoaderListeners.isEmpty()) {
            listeners.clear();
            for (WatchKey wk : keys.keySet()) {
                try {
                    wk.cancel();
                } catch (Exception e) {
                    LOGGER.error("Ooops", e);
                }
            }
            snapshots.clear();
            try {
                this.watcher.close();
            } catch (IOException e) {
                LOGGER.error("Ooops", e);
            }
            LOGGER.info("All classloaders closed, released watch service..");
            try {
                // Reset
                this.watcher = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                LOGGER.error("Ooops", e);
            }
        }
        LOGGER.debug("All watch listeners removed for classLoader {}", classLoader);
    }

    /**
     * Registers the given directory
     */
    public void addDirectory(Path path) throws IOException {
       registerAll(path, false);
    }

    protected abstract void registerAll(final Path dir, boolean fromCreateEvent) throws IOException;

    /**
     * Recovers from the events that the watch service dropped for the key of the given directory. Calls
     * {@link #rescanFile} for each file.
     *
     * @param dir      the directory of the key
     * @param previous the state of the files after the previous overflow, null if the key did not overflow yet
     * @param current  the state of the files to fill
     */
    protected abstract void rescan(Path dir, Snapshot previous, Snapshot current);

    /**
     * Sends the events for a file found by a rescan, unless it is unchanged since the previous one.
     *
     * @param attrs the attributes of the file if known, may be null
     */
    protected void rescanFile(Path file, BasicFileAttributes attrs, Snapshot previous, Snapshot current) {
        FileState state = FileState.read(file, attrs);
        if (state == null) {
            // not a regular file, or deleted in the meantime
            return;
        }
        current.put(file, state);
        FileState previousState = previous == null ? null : previous.get(file);
        if (previousState == null || previousState.isRacy() || !state.equals(previousState)) {
            dispatchRescanEvents(file);
        }
    }

    /**
     * Sends DELETE events for the files of the previous rescan that are gone. Their events may be dropped too.
     */
    private void dispatchDeleted(Snapshot previous, Snapshot current) {
        previous.forEachFile(file -> {
            // A file that cannot be checked is not taken as deleted, e.g. if the rescan could not read its directory.
            if (current.get(file) == null && Files.notExists(file, NOFOLLOW_LINKS)) {
                dispatchFakeEvent(ENTRY_DELETE, file);
            }
        });
    }

    protected void registerCreatedDirectory(Path dir) {
        try {
            registerAll(dir, true);
        } catch (IOException e) {
            LOGGER.warning("Unable to register events for directory {}", e, dir);
        }
    }

    /**
     * Registers a directory that exists but has lost its watch, as a directory that was deleted and created again
     * has. On macOS such a directory is reported as modified, not created.
     */
    protected void registerIfNotWatched(Path dir) {
    }

    protected void dispatchFakeEvent(WatchEvent.Kind<Path> kind, Path file) {
        LOGGER.debug("Fake watch event '{}' on '{}'", kind.name(), file);
        dispatcher.add(new HotswapAgentWatchEvent<>(kind, file.getFileName()), file);
    }

    protected void dispatchRescanEvents(Path file) {
        // The dropped event could be either CREATE or MODIFY, and listeners usually handle only one of them.
        dispatchFakeEvent(ENTRY_CREATE, file);
        dispatchFakeEvent(ENTRY_MODIFY, file);
    }

    /**
     * Process all events for keys queued to the watcher
     *
     * @return true if should continue
     * @throws InterruptedException
     */
    boolean processEvents() throws InterruptedException {

        // wait for key to be signaled
        WatchKey key;
        try {
            key = watcher.poll(10, TimeUnit.MILLISECONDS);
        } catch (ClosedWatchServiceException e) {
            // the watch service was released after all classloaders were closed and is being replaced
            // by a new one, see closeClassLoader(). Keep running and poll the new instance.
            return true;
        }
        if (key == null) {
            return true;
        }

        Path dir = keys.get(key);

        if (dir == null) {
            LOGGER.warning("WatchKey '{}' not recognized", key);
            return true;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();

            if (kind == OVERFLOW) {
                LOGGER.warning("WatchKey '{}' overflowed, rescanning {}", key, dir);
                overflowCount++;
                Snapshot previous = snapshots.get(key);
                Snapshot current = new Snapshot(dir);
                rescan(dir, previous, current);
                if (previous != null) {
                    dispatchDeleted(previous, current);
                }
                snapshots.put(key, current);
                // the key may have been cancelled meanwhile by closeClassLoader()
                if (!key.isValid()) {
                    snapshots.remove(key, current);
                }
                continue;
            }

            // Context for directory entry event is the file name of entry
            WatchEvent<Path> ev = cast(event);
            Path name = ev.context();
            Path child = dir.resolve(name);

            LOGGER.debug("Watch event '{}' on '{}' --> {}", event.kind().name(), child, name);

            dispatcher.add(ev, child);

            boolean directory = kind != ENTRY_DELETE && Files.isDirectory(child, NOFOLLOW_LINKS);

            Snapshot snapshot = snapshots.get(key);
            if (snapshot != null) {
                if (kind == ENTRY_DELETE) {
                    snapshot.remove(child);
                } else if (!directory) {
                    // changed since the rescan, the next one sends it again
                    snapshot.put(child, FileState.DIRTY);
                }
            }

            if (directory && kind == ENTRY_CREATE) {
                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                registerCreatedDirectory(child);
            } else if (directory && kind == ENTRY_MODIFY) {
                // macOS reports a directory that was deleted and created again as modified, not created
                registerIfNotWatched(child);
            }
        }

        // reset key and remove from set if directory no longer accessible
        boolean valid = key.reset();
        if (!valid) {
            LOGGER.debug("Watcher on {} not valid, removing path=", keys.get(key));
            keys.remove(key);
            snapshots.remove(key);
            // the directory may have been created again before its watch reported that it was deleted
            if (!classLoaderListeners.isEmpty() && Files.isDirectory(dir, NOFOLLOW_LINKS)) {
                registerIfNotWatched(dir);
            }
            // all directories are inaccessible
            if (keys.isEmpty()) {
                return false;
            }
            if (classLoaderListeners.isEmpty()) {
                for (WatchKey k : keys.keySet()) {
                    k.cancel();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void run() {

        runner = new Thread() {
            @Override
            public void run() {
                try {
                    for (;;) {
                        if (stopped || !processEvents()) {
                            break;
                        }
                    }
                } catch (InterruptedException x) {

                } finally {
                    // the events are not processed anymore, so the state of the files is not kept up to date
                    snapshots.clear();
                }
            }
        };
        runner.setDaemon(true);
        runner.setName("HotSwap Watcher");
        runner.start();

        dispatcher.start();
    }

    @Override
    public void stop() {
        stopped = true;
    }

    /**
     * The state of a file. A change of the file changes its size, modification time or change time (ctime). The
     * ctime changes also when a file is unpacked or copied with its old modification time. Without ctime (Windows)
     * the creation time is compared too, there a file overwritten with the same size and the same modification time
     * as before is taken as unchanged.
     */
    protected static final class FileState {
        // File times may be coarse (e.g. a clock tick or a second), a file changed again within the same time
        // keeps the same state. A state read this short after the file changed is not trusted, like racily clean
        // entries in git.
        static final long RACY_MARGIN_NANOS = TimeUnit.SECONDS.toNanos(2);

        /**
         * The state of a file that got an event since the rescan, so it has changed and the next rescan sends it
         * again. Its attributes are not read: the file may still be written, and the file times of a network file
         * system may come from another clock, so the racy margin cannot be trusted for it.
         */
        static final FileState DIRTY = new FileState(-1, -1, -1, -1, true, true);

        private final long size;
        private final long modified;
        private final long created;
        private final long changed;
        private final boolean racy;
        // by the modification time only, chmod sets the change time
        private final boolean modifiedRacy;

        private FileState(long size, long modified, long created, long changed, boolean racy, boolean modifiedRacy) {
            this.size = size;
            this.modified = modified;
            this.created = created;
            this.changed = changed;
            this.racy = racy;
            this.modifiedRacy = modifiedRacy;
        }

        private FileState(long size, FileTime modified, FileTime created, FileTime changed, long readAt) {
            this.size = size;
            this.modified = toNanos(modified);
            this.created = toNanos(created);
            this.changed = changed == null ? -1 : toNanos(changed);
            this.racy = Math.max(this.modified, Math.max(this.created, this.changed)) >= readAt - RACY_MARGIN_NANOS;
            this.modifiedRacy = this.modified >= readAt - RACY_MARGIN_NANOS;
        }

        /**
         * Reads the state of a file.
         *
         * @param attrs the attributes of the file if known, may be null
         * @return the state, or null if it is not a regular file or it does not exist (anymore)
         */
        static FileState read(Path file, BasicFileAttributes attrs) {
            // taken before the attributes are read, a pause after it must not make the file look older
            long readAt = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
            try {
                if (UNIX_VIEW) {
                    try {
                        // one read for all, the size and the times then belong to the same version of the file
                        Map<String, Object> unix = Files.readAttributes(file,
                                "unix:isRegularFile,size,lastModifiedTime,creationTime,ctime");
                        if (!Boolean.TRUE.equals(unix.get("isRegularFile"))) {
                            return null;
                        }
                        return new FileState((Long) unix.get("size"), (FileTime) unix.get("lastModifiedTime"),
                                (FileTime) unix.get("creationTime"), (FileTime) unix.get("ctime"), readAt);
                    } catch (SecurityException e) {
                        // a security manager without the permission accessUserInformation, go on without ctime
                    }
                }
                if (attrs == null) {
                    attrs = Files.readAttributes(file, BasicFileAttributes.class);
                }
                if (!attrs.isRegularFile()) {
                    return null;
                }
                return new FileState(attrs.size(), attrs.lastModifiedTime(), attrs.creationTime(), null, readAt);
            } catch (IOException e) {
                return null;
            }
        }

        boolean isRacy() {
            return racy;
        }

        /**
         * Tells whether at most the attributes of the file changed since the previous state, e.g. by chmod: the
         * change time is not compared. A previous state read short after the file was modified is not trusted.
         * A file written again with the same size and its old modification time, e.g. by cp -p, is not recognized.
         */
        boolean hasContentOf(FileState previous) {
            return !previous.modifiedRacy && size == previous.size && modified == previous.modified
                    && created == previous.created;
        }

        private static long toNanos(FileTime time) {
            return time.to(TimeUnit.NANOSECONDS);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileState)) {
                return false;
            }
            FileState that = (FileState) o;
            return size == that.size && modified == that.modified && created == that.created && changed == that.changed;
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, modified, created, changed);
        }
    }

    /**
     * The state of the files of a key. The files are kept by their directory and file name: a deleted file or
     * directory is removed without a look at the other files, and the path of the directory is kept only once.
     * It is used only by the thread that processes the events.
     */
    protected static final class Snapshot {
        private final Path root;
        private final Map<Path, Directory> directories = new HashMap<>();

        /**
         * @param root the directory of the key
         */
        Snapshot(Path root) {
            this.root = root;
        }

        FileState get(Path file) {
            Directory directory = directories.get(file.getParent());
            return directory == null ? null : directory.files.get(file.getFileName());
        }

        void put(Path file, FileState state) {
            directory(file.getParent()).files.put(file.getFileName(), state);
        }

        /**
         * Removes a deleted file, or a deleted directory with all files under it. The files of a FILE_TREE key
         * under a directory that is moved away get no events of their own.
         */
        void remove(Path path) {
            Directory parent = directories.get(path.getParent());
            if (parent != null) {
                if (parent.files.remove(path.getFileName()) != null) {
                    return;
                }
                parent.directories.remove(path);
            }
            removeDirectory(path);
        }

        void forEachFile(Consumer<Path> action) {
            for (Entry<Path, Directory> entry : directories.entrySet()) {
                for (Path name : entry.getValue().files.keySet()) {
                    action.accept(entry.getKey().resolve(name));
                }
            }
        }

        private Directory directory(Path dir) {
            Directory directory = directories.get(dir);
            if (directory == null) {
                directory = new Directory();
                directories.put(dir, directory);
                // known to all its parents up to the root, so that a deleted parent removes it
                Path parent = dir.getParent();
                if (parent != null && !dir.equals(root)) {
                    directory(parent).directories.add(dir);
                }
            }
            return directory;
        }

        private void removeDirectory(Path dir) {
            Directory directory = directories.remove(dir);
            if (directory != null) {
                for (Path child : directory.directories) {
                    removeDirectory(child);
                }
            }
        }

        private static final class Directory {
            // by file name
            final Map<Path, FileState> files = new HashMap<>();
            // the directories in this one that have files in or under them
            final Set<Path> directories = new HashSet<>();
        }
    }

    /**
     * Get a Watch event modifier. These are platform specific and hiden in sun api's
     *
     * @see <a href="https://github.com/HotswapProjects/HotswapAgent/issues/41">
     *      Issue#41</a>
     * @see <a href=
     *      "http://stackoverflow.com/questions/9588737/is-java-7-watchservice-slow-for-anyone-else">
     *      Is Java 7 WatchService Slow for Anyone Else?</a>
     */
    static WatchEvent.Modifier getWatchEventModifier(String claz, String field) {
        try {
            Class<?> c = Class.forName(claz);
            Field f = c.getField(field);
            return (WatchEvent.Modifier) f.get(c);
        } catch (Exception e) {
            return null;
        }
    }
}
