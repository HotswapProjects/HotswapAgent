package org.hotswap.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Information about hotswap agent version.
 *
 * @author Jiri Bubnik
 */
public class Version {

    /**
     * Return current version.
     * @return the version.
     */
    public static String version() {
        try {
            Properties prop = new Properties();
            InputStream in = Version.class.getResourceAsStream("/version.properties");
            prop.load(in);
            in.close();

            return prop.getProperty("version");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
