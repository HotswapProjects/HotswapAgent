package org.hotswap.agent.util.classloader;

import java.net.URL;

/**
 * @author Jiri Bubnik
 */
public interface ExtendableClassLoader {
    public void addURL(URL url, boolean end);

}
