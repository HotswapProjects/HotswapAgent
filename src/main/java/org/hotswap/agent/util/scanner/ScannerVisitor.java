package org.hotswap.agent.util.scanner;

import java.io.IOException;
import java.io.InputStream;

/**
 * Visit a file found by a scanner.
 *
 * @author Jiri Bubnik
 */
public interface ScannerVisitor {
    /**
     * Visit the file.
     *
     * @param file the file
     * @throws IOException IO exception while working with the file stream.
     */
    public void visit(InputStream file) throws IOException;
}
