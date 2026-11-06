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
package org.hotswap.agent.plugin.jersey2.oneshot;

import java.io.InputStream;
import java.util.NoSuchElementException;

import org.glassfish.jersey.server.ResourceFinder;

/**
 * Finds {@link OneShotResource} once and cannot be reset, as JarFileScanner of Jersey.
 */
public class OneShotFinder implements ResourceFinder {

    private static final String RESOURCE = OneShotResource.class.getName().replace('.', '/') + ".class";

    private boolean found;

    @Override
    public boolean hasNext() {
        return !found;
    }

    @Override
    public String next() {
        if (found) {
            throw new NoSuchElementException();
        }
        found = true;
        return RESOURCE;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream open() {
        return OneShotFinder.class.getClassLoader().getResourceAsStream(RESOURCE);
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }
}
