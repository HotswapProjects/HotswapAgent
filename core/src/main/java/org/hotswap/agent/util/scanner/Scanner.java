package org.hotswap.agent.util.scanner;

import java.io.IOException;

/**
 * Scan a classpath directory for files and call visitor for each found file.
 * <p/>
 * The directory may reside in multiple locations (classpath, JAR directory, ...).
 * All locations will be visited.
 *
 * @author Jiri Bubnik
 */
public interface Scanner {
    /**
     * Scan all directories matching path (there might be multiple locations on classpath, JAR directory, ...)
     * and call visitor for each found file.
     *
     * @param classLoader classloader to resolve path
     * @param path        a '/'-separated path name that identifies the resource directory.
     *                    Semantics same as {@link java.lang.ClassLoader#getResources}.
     * @param visitor     visit each file on the path
     * @throws IOException any IO exception while scanning
     */
    public void scan(ClassLoader classLoader, String path, ScannerVisitor visitor) throws IOException;
}
