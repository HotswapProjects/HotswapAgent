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
