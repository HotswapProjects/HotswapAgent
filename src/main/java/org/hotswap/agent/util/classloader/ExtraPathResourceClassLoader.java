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
 * This classloader delegates getResource() and getResources() calls to
 *
 * @author Jiri Bubnik
 */
public class ExtraPathResourceClassLoader extends ClassLoader {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ExtraPathResourceClassLoader.class);

    /**
     * Set of all changed URLs.
     */
    Set<URL> changedUrls = new HashSet<URL>();


    ClassLoader watchResourcesClassLoader;

    /**
     * New instance with urls and watcher service.
     *
     * @param watchResources the URLs from which to load resources
     * @param watcher        watcher service to register watch events
     */
    public void init(URL[] watchResources, Watcher watcher) {

        this.watchResourcesClassLoader = new URLClassLoader(watchResources);
        // register watch resources - each modified resource will be added to changedUrls.
        for (URL resource : watchResources) {
            try {
                URI uri = resource.toURI();
                watcher.addDirectory(uri);
                LOGGER.debug("Watching directory '{}' for changes.", uri);
                watcher.addEventListener(uri, new WatchEventListener() {
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
            } catch (IOException e) {
                LOGGER.error("Unable to add URL {} to the watcher. URL is skipped.", e, resource);
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
            LOGGER.trace("watchResource - using changed resource {}", name);
            return resource;
        } else {
            return super.getResource(name);
        }
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
            LOGGER.trace("watchResource - using changed resource {}", name);
            return res.elements();
        } else
            return super.getResources(name);
    }
}
