package org.hotswap.agent.util.classloader;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * Special URL classloader to get only changed resources from URL.
 *
 * Use this classloader to support watchResources property.
 *
 * This classloader checks if the resource was modified after application startup and in that case
 * delegates getResource()/getResources() to custom URL classloader. Otherwise returns null or resource
 * from paren classloader (depending on searchParent property).
 *
 * @author Jiri Bubnik
 */
public class WatchResourcesClassLoader extends ClassLoader {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatchResourcesClassLoader.class);

    /**
     * URLs of changed resources. Use this set to check if the resource was changed and hence should
     * be returned by this classloader.
     */
    Set<URL> changedUrls = new HashSet<URL>();

    /**
     * Watch for requested resource in parent classloader in case it is not found by this classloader?
     * Note that there is child first precedence anyway.
     */
    boolean searchParent = true;

    public void setSearchParent(boolean searchParent) {
        this.searchParent = searchParent;
    }

    /**
     * URL classloader configured to get resources only from exact set of URL's (no parent delegation)
     */
    ClassLoader watchResourcesClassLoader;

    public WatchResourcesClassLoader() {
    }

    public WatchResourcesClassLoader(boolean searchParent) {
        this.searchParent = searchParent;
    }

    /**
     * Configure new instance with urls and watcher service.
     *
     * @param watchResources the URLs from which to load resources
     * @param watcher        watcher service to register watch events
     */
    public void init(URL[] watchResources, Watcher watcher) {
        // create classloader to serve resources only from watchResources URL's
        this.watchResourcesClassLoader = new WatchResourcesUrlClassLoader(watchResources);

        // register watch resources - on change event each modified resource will be added to changedUrls.
        for (URL resource : watchResources) {
            try {
                URI uri = resource.toURI();
                LOGGER.debug("Watching directory '{}' for changes.", uri);
                watcher.addEventListener(this, uri, new WatchEventListener() {
                    @Override
                    public void onEvent(WatchEvent event) {
                        try {
                            if (event.isFile() || event.isDirectory()) {
                                changedUrls.add(event.getURI().toURL());
                                LOGGER.trace("File '{}' changed and will be returned instead of original classloader equivalent.", event.getURI().toURL());
                            }
                        } catch (MalformedURLException e) {
                            LOGGER.error("Unexpected - cannot convert URI {} to URL.", e, event.getURI());
                        }
                    }
                });
            } catch (URISyntaxException e) {
                LOGGER.warning("Unable to convert watchResources URL '{}' to URI. URL is skipped.", e, resource);
            }
        }
    }

    /**
     * Check if the resource was changed after this classloader instantiaton.
     *
     * @param url full URL of the file
     * @return true if was changed after instantiation
     */
    public boolean isResourceChanged(URL url) {
        return changedUrls.contains(url);
    }

    /**
     * Returns URL only if the resource is found in changedURL and was actually changed after
     * instantiation of this classloader.
     */
    @Override
    public URL getResource(String name) {
        URL resource = watchResourcesClassLoader.getResource(name);
        if (resource != null && isResourceChanged(resource)) {
            LOGGER.trace("watchResources - using changed resource {}", name);
            return resource;
        } else if (searchParent) {
            return super.getResource(name);
        } else
            return null;
    }

    /**
     * Returns only a single instance of the changed resource.
     * There are conflicting requirements for other resources inclusion. This class
     * should "hide" the original resource, hence it should not be included in the resoult.
     * On the other hand, there may be resource with the same name in other JAR which
     * should be included and now is hidden (for example multiple persistence.xml).
     * Maybe a new property to influence this behaviour?
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        URL resource = watchResourcesClassLoader.getResource(name);
        if (resource != null && isResourceChanged(resource)) {
            Vector<URL> res = new Vector<URL>();
            res.add(resource);
            LOGGER.trace("watchResources - using changed resource {}", name);
            return res.elements();
        } else if (searchParent) {
            return super.getResources(name);
        } else {
            return null;
        }

    }


    /**
     * Helper classloader to get resources from list of urls only.
     */
    public static class WatchResourcesUrlClassLoader extends URLClassLoader {
        public WatchResourcesUrlClassLoader(URL[] urls) {
            super(urls);
        }

        // do not use parent resource (may introduce infinite loop)
        @Override
        public URL getResource(String name) {
            return findResource(name);
        }
    };
}
