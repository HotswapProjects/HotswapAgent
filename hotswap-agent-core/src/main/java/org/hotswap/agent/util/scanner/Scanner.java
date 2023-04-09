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
    void scan(ClassLoader classLoader, String path, ScannerVisitor visitor) throws IOException;
}
