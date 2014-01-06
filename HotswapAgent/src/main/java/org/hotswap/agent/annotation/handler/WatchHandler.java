package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginManager;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

/**
 * Watch method handler - handle @Watch annotation on a method.
 *
 * @author Jiri Bubnik
 */
public class WatchHandler implements PluginHandler<Watch> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WatchHandler.class);

    protected PluginManager pluginManager;

    public WatchHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean initField(PluginAnnotation<Watch> pluginAnnotation) {
        throw new IllegalAccessError("@Transform annotation not allowed on fields.");
    }


    @Override
    public boolean initMethod(final PluginAnnotation<Watch> pluginAnnotation) {
        LOGGER.debug("Init for method " + pluginAnnotation.getMethod());

        ClassLoader classLoader = pluginManager.getPluginRegistry().getAppClassLoader(pluginAnnotation.getPlugin());

        try {
            registerResources(pluginAnnotation, classLoader);
        } catch (IOException e) {
            LOGGER.error("Unable to register resources for annotation {} on method {} class {}", e,
                    pluginAnnotation.getAnnotation(),
                    pluginAnnotation.getMethod().getName(),
                    pluginAnnotation.getMethod().getDeclaringClass().getName());
            return false;
        }

        return true;
    }

    /**
     * Register resource change listener on URI:
     * - classpath (already should contain extraClasspath)
     * - plugin configuration - watchResources property
     */
    private void registerResources(final PluginAnnotation<Watch> pluginAnnotation, final ClassLoader classLoader) throws IOException {
        final Watch annot = pluginAnnotation.getAnnotation();

        // normalize
        String path = annot.path();
        if (path.equals(".") || path.equals("/"))
            path = "";
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 2);


        // classpath resources (already should contain extraClasspath)
        Enumeration<URL> en = classLoader.getResources(path);
        while (en.hasMoreElements()) {
            try {
                URI uri = en.nextElement().toURI();
                LOGGER.debug("Registering resource listener on classpath URI {}", uri);
                registerResourceListener(pluginAnnotation, classLoader, uri);
            } catch (URISyntaxException e) {
                LOGGER.error("Unable convert root resource path URL to URI", e);
            }
        }

        for (URL url : pluginManager.getPluginConfiguration(classLoader).getWatchResources()) {
            try {
                Path watchResourcePath = Paths.get(url.toURI());
                Path pathInWatchResource = watchResourcePath.resolve(path);
                LOGGER.debug("Registering resource listener on watchResources URI {}", pathInWatchResource.toUri());
                registerResourceListener(pluginAnnotation, classLoader, pathInWatchResource.toUri());
            } catch (URISyntaxException e) {
                LOGGER.error("Unable convert watch resource path URL {} to URI", e, url);
            }
        }
    }

    /**
     * Using pluginManager.registerResourceListener() add new listener on URI.
     * <p/>
     * There might be several same events for a resource change (either from filesystem or when IDE clears and reloads
     * a class multiple time on rebuild). Use command scheduler to group same events into single invocation.
     */
    private void registerResourceListener(final PluginAnnotation<Watch> pluginAnnotation, final ClassLoader classLoader, URI uri) throws IOException {
        pluginManager.registerResourceListener(uri, new WatchEventListener() {
            @Override
            public void onEvent(WatchEvent event) {
                if (event.isFile()) {
                    Command command = new WatchEventCommand(pluginAnnotation, event, classLoader);
                    pluginManager.getScheduler().scheduleCommand(command);
                    LOGGER.trace("Resource changed {}", event);
                }
            }
        });
    }

}
