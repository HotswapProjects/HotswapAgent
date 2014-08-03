package org.hotswap.agent.watch.nio;


import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * NIO2 watcher implementation.
 * <p/>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * <p/>
 * By http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 * @author Jiri Bubnik
 */
public class WatcherNIO2 implements Watcher {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatcherNIO2.class);

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final Map<Path, List<WatchEventListener>> listeners = new HashMap<Path, List<WatchEventListener>>();

    // keep track about which classloader requested which event
    protected Map<WatchEventListener, ClassLoader> classLoaderListeners = new HashMap<WatchEventListener, ClassLoader>();


    Thread runner;
    boolean stopped;

    public WatcherNIO2() throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
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
            // toString() is weird and solves HiarchicalUriException for URI like "file:./src/resources/file.txt".
            path = new File(pathPrefix);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to watch for path {}, not a local regular file or directory.", pathPrefix);
            LOGGER.trace("Unable to watch for path {} exception", e, pathPrefix);
            return;
        }

        try {
            addDirectory(path.toURI());
        } catch (IOException e) {
            LOGGER.error("Unable to watch path with prefix '{}' for changes.", e, pathPrefix);
            return;
        }

        List<WatchEventListener> list = listeners.get(Paths.get(pathPrefix));
        if (list == null) {
            list = new ArrayList<WatchEventListener>();
            listeners.put(Paths.get(pathPrefix), list);
        }
        list.add(listener);

        if (classLoader != null)
            classLoaderListeners.put(listener, classLoader);
    }

    @Override
    public void addEventListener(ClassLoader classLoader, URL pathPrefix, WatchEventListener listener) {
        try {
            addEventListener(classLoader, pathPrefix.toURI(), listener);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to convert URL to URI " + pathPrefix, e);
        }
    }

    /**
     * Remove all transformers registered with a classloader
     * @param classLoader
     */
    public void closeClassLoader(ClassLoader classLoader) {
        for (Iterator<Map.Entry<WatchEventListener, ClassLoader>> entryIterator = classLoaderListeners.entrySet().iterator();
             entryIterator.hasNext(); ) {
            Map.Entry<WatchEventListener, ClassLoader> entry = entryIterator.next();
            if (entry.getValue().equals(classLoader)) {
                entryIterator.remove();
                for (List<WatchEventListener> transformerList : listeners.values())
                    transformerList.remove(entry.getKey());
            }
        }

        LOGGER.debug("All watch listeners removed for classLoader {}", classLoader);
    }

    /**
     * Registers the given directory
     */
    public void addDirectory(URI path) throws IOException {
        try {
            Path dir = Paths.get(path);

            if (keys.values().contains(dir))
                return;

            registerAll(dir);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URI format " + path, e);
        } catch (FileSystemNotFoundException e) {
            throw new IOException("Invalid URI " + path, e);
        } catch (SecurityException e) {
            throw new IOException("Security exception for URI " + path, e);
        }
    }


    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        // check duplicate registration
        if (keys.values().contains(dir))
            return;

        // try to set high sensitivity
        WatchEvent.Modifier high = get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH();
        WatchKey key =
                (high == null) ?
                        dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) :
                        dir.register(watcher, new WatchEvent.Kind<?>[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}, high);

        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    /**
     * Process all events for keys queued to the watcher
     *
     * @return true if should continue
     */
    private boolean processEvents() {

        // wait for key to be signalled
        WatchKey key;
        try {
            key = watcher.take();
        } catch (InterruptedException x) {
            return false;
        }


        Path dir = keys.get(key);
        if (dir == null) {
            LOGGER.warning("WatchKey '{}' not recognized", key);
            return true;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if (kind == OVERFLOW) {
                LOGGER.warning("WatchKey '{}' overflowed", key);
                continue;
            }

            // Context for directory entry event is the file name of entry
            WatchEvent<Path> ev = cast(event);
            Path name = ev.context();
            Path child = dir.resolve(name);

            LOGGER.trace("Watch event '{}' on '{}'", event.kind().name(), child);

            callListeners(event, child);

            // if directory is created, and watching recursively, then
            // register it and its sub-directories
            if (kind == ENTRY_CREATE) {
                try {
                    if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        registerAll(child);
                    }
                } catch (IOException x) {
                    LOGGER.warning("Unable to register events for directory {}", x, child);
                }
            }
        }

        // reset key and remove from set if directory no longer accessible
        boolean valid = key.reset();
        if (!valid) {
            keys.remove(key);

            // all directories are inaccessible
            if (keys.isEmpty()) {
                return false;
            }
        }

        return true;

    }

    // notify listeners about new event
    private void callListeners(final WatchEvent event, final Path path) {
        for (Map.Entry<Path, List<WatchEventListener>> list : listeners.entrySet()) {
            for (WatchEventListener listener : list.getValue()) {
                if (path.startsWith(list.getKey())) {
                    org.hotswap.agent.watch.WatchEvent agentEvent = new HotswapWatchEvent(event, path);

                    try {
                        listener.onEvent(agentEvent);
                    } catch (Throwable e) {
                        LOGGER.error("Error in watch event '{}' listener '{}'", e, agentEvent, listener);
                    }
                }
            }
        }
    }

    // translate constants between NIO event and ageent event
    private static org.hotswap.agent.watch.WatchEvent.WatchEventType toAgentEvent(WatchEvent.Kind kind) {
        if (kind == ENTRY_CREATE)
            return org.hotswap.agent.watch.WatchEvent.WatchEventType.CREATE;
        else if (kind == ENTRY_MODIFY)
            return org.hotswap.agent.watch.WatchEvent.WatchEventType.MODIFY;
        else if (kind == ENTRY_DELETE)
            return org.hotswap.agent.watch.WatchEvent.WatchEventType.DELETE;
        else
            throw new IllegalArgumentException("Unknown event type " + kind.name());
    }

    @Override
    public void run() {
        runner = new Thread() {
            @Override
            public void run() {
                for (; ; ) {
                    if (stopped || !processEvents())
                        break;
                }
            }
        };

        runner.setDaemon(true);
        runner.start();
    }

    @Override
    public void stop() {
        stopped = true;
    }

    /**
     * Filesystem event.
     */
    public static class HotswapWatchEvent implements org.hotswap.agent.watch.WatchEvent {

        private final WatchEvent event;
        private final Path path;

        public HotswapWatchEvent(WatchEvent event, Path path) {
            this.event = event;
            this.path = path;
        }

        @Override
        public WatchEventType getEventType() {
            return toAgentEvent(event.kind());
        }

        @Override
        public URI getURI() {
            return path.toUri();
        }

        @Override
        public boolean isFile() {
            //return Files.isRegularFile(path); - did not work in some cases
            return !isDirectory();
        }

        @Override
        public boolean isDirectory() {
            return Files.isDirectory(path);
        }

        @Override
        public String toString() {
            return "WatchEvent on path " + path + " for event " + event.kind();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HotswapWatchEvent that = (HotswapWatchEvent) o;

            if (!event.equals(that.event)) return false;
            if (!path.equals(that.path)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = event.hashCode();
            result = 31 * result + path.hashCode();
            return result;
        }
    }

    /**
     * Get modifier for high sensitivity on Watch events.
     *
     * @see <a href="https://github.com/HotswapProjects/HotswapAgent/issues/41">Issue#41</a>
     * @see <a href="http://stackoverflow.com/questions/9588737/is-java-7-watchservice-slow-for-anyone-else">Is Java 7 WatchService Slow for Anyone Else?</a>
     */
    WatchEvent.Modifier get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH() {
        try {
            Class<?> c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
            Field f = c.getField("HIGH");
            return (WatchEvent.Modifier) f.get(c);
        } catch (Exception e) {
            return null;
        }
    }
}