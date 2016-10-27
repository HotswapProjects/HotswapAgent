/*
 * Copyright 2016 the original author or authors.
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

/**
 * <p>
 * The PathPair is used for the windows watcher implementation. In ntfs the
 * watched directories are locked so deleting them is impossible. The windows
 * watcher watches one directory down ( at the expense of getting many more
 * events) and this class holds the mapping between the <code>target</code>
 * directory (the one we intend to listen events) vs the actually
 * <code>watched</code> directory.
 * </p>
 * <p>
 * This workaround, does not completely eliminate the locking problem. For
 * example, when working with maven projects one first needs to clean and then
 * build (two commands).
 *
 * In eclipse, sometimes the m2e plugin complains that it can not access
 * generated files. This is usually fixed with a clean.
 * </p>
 *
 * @author alpapad@gmail.com
 */
public class PathPair {

    /** The target path. */
    private final Path target;

    /** The watched path. */
    private final Path watched;

    /**
     * Factory method for creating target-target path pair
     *
     * @param target
     * @return the path pair
     */
    public static PathPair get(Path target) {
        return new PathPair(target);
    }

    /**
     * Factory method for creating a target-watched path pair
     *
     * @param target the target path
     * @param watched the watched path
     * @return the path pair
     */
    public static PathPair get(Path target, Path watched) {
        return new PathPair(target, watched);
    }

    /**
     * Instantiates a new path pair.
     *
     * @param target
     *            the target
     */
    public PathPair(Path target) {
        this(target, target);
    }

    /**
     * Instantiates a new path pair.
     *
     * @param target  the target path
     * @param watched the watched path
     */
    public PathPair(Path target, Path watched) {
        this.target = target;
        this.watched = watched;
    }

    /**
     * Gets the watched path.
     *
     * @return the watched path
     */
    public Path getWatched() {
        return watched;
    }

    /**
     * Gets the target path
     *
     * @return the target path
     */
    public Path getTarget() {
        return target;
    }

    /**
     * Resolve a relative path (as returned by the java watcher) to an absolute path
     *
     * @param other
     *            the other
     * @return the path
     */
    public Path resolve(Path other) {
        return watched.resolve(other);
    }

    /**
     * Checks if the parameter path is being watched.
     *
     * @param target
     *            the target
     * @return true, if is watching
     */
    public boolean isWatching(Path target) {
        return target.startsWith(watched);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (watched == null ? 0 : watched.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PathPair other = (PathPair) obj;
        if (watched == null) {
            if (other.watched != null) {
                return false;
            }
        } else if (!watched.equals(other.watched)) {
            return false;
        }
        return true;
    }

    public String getShortDescription() {
        if (watched != null && watched.equals(target)) {
            // short description for NIO2 implementation
            return "PathPair [watched=" + watched + "]";
        }
        return "PathPair [target=" + target + ", watched=" + watched + "]";
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "PathPair [target=" + target + ", watched=" + watched + "]";
    }
}
