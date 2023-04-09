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
package org.hotswap.agent.watch;

import org.hotswap.agent.watch.nio.TreeWatcherNIO;
import org.hotswap.agent.watch.nio.WatcherNIO2;

import java.io.IOException;

/**
 * Resolve watcher from java version (Java NIO2 implementation is preferred if available.)
 *
 * @author Jiri Bubnik
 */
public class WatcherFactory {

    public static double JAVA_VERSION = getVersion();

    static double getVersion() {
        String version = System.getProperty("java.version");

        int pos = 0;
        boolean decimalPart = false;

        for (; pos < version.length(); pos++) {
            char c = version.charAt(pos);
            if ((c < '0' || c > '9') && c != '.') break;
            if (c == '.') {
                if (decimalPart) break;
                decimalPart = true;
            }
        }
        return Double.parseDouble(version.substring(0, pos));
    }

    public static boolean IS_WINDOWS = isWindows();

    static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    public Watcher getWatcher() throws IOException {
        if (JAVA_VERSION >= 1.7) {
            if (IS_WINDOWS) {
                return new TreeWatcherNIO();
            } else {
                return new WatcherNIO2();
            }
        } else {
            throw new UnsupportedOperationException("Watcher is implemented only for Java 1.7 (NIO2). " +
                    "JNotify implementation should be added in the future for older Java version support.");
        }

    }
}
