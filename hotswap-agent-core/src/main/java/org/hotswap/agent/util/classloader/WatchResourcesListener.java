package org.hotswap.agent.util.classloader;

import java.net.URL;

public interface WatchResourcesListener {
    void onFileChange(URL url);
}
