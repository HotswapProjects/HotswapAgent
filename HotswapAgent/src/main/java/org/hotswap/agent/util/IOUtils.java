package org.hotswap.agent.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * IO utils (similar to apache commons).
 */
public class IOUtils {

    /**
     * Download URI to byte array.
     *
     * @param uri uri to process
     * @return byte array
     * @throws IllegalArgumentException for download problems
     */
    public static byte[] toByteArray(URI uri) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] chunk = new byte[4096];
            int bytesRead;
            InputStream stream = uri.toURL().openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        return outputStream.toByteArray();
    }
}
