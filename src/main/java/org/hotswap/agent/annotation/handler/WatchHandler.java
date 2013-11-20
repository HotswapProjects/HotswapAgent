package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginManager;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
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
                continue;
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
                continue;
            }
        }
    }

    private void registerResourceListener(final PluginAnnotation<Watch> pluginAnnotation, final ClassLoader classLoader, URI uri) throws IOException {
        pluginManager.registerResourceListener(uri, new WatchEventListener() {
            @Override
            public void onEvent(WatchEvent event) {
                onWatchEvent(pluginAnnotation, event, classLoader);
                LOGGER.trace("Resource changed {}", event);
            }
        });
    }

    public void onWatchEvent(PluginAnnotation<Watch> pluginAnnotation, WatchEvent event, ClassLoader classLoader) {
        final Watch annot = pluginAnnotation.getAnnotation();
        Object plugin = pluginAnnotation.getPlugin();

        // regular files filter
        if (annot.onlyRegularFiles() && !event.isFile())
            return;

        // watch type filter
        if (!Arrays.asList(annot.watchEvents()).contains(event.getEventType()))
            return;

        // resource nam filter regexp
        if (annot.filter() != null && annot.filter().length() > 0) {
            if (!event.getURI().toString().matches(annot.filter()))
                return;
        }

        LOGGER.debug("Executing resource changed method {} on class {} for event {}",
                pluginAnnotation.getMethod().getName(), plugin.getClass().getName(), event);

        //we may need to crate CtClass on behalf of the client and close it after invocation.
        CtClass ctClass = null;

        List<Object> args = new ArrayList<Object>();
        for (Class<?> type : pluginAnnotation.getMethod().getParameterTypes()) {
            if (type.isAssignableFrom(ClassLoader.class)) {
                args.add(classLoader);
            } else if (type.isAssignableFrom(CtClass.class)) {
                try {
                    ctClass = createCtClass(event.getURI(), classLoader);
                    args.add(ctClass);
                } catch (Exception e) {
                    LOGGER.error("Unable create CtClass for URI '{}'.", e, event.getURI());
                    return;
                }
            } else if (type.isAssignableFrom(URI.class)) {
                args.add(event.getURI());
            } else if (type.isAssignableFrom(URL.class)) {
                try {
                    args.add(event.getURI().toURL());
                } catch (MalformedURLException e) {
                    LOGGER.error("Unable to convert URI '{}' to URL.", e, event.getURI());
                    return;
                }
            } else if (type.isAssignableFrom(ClassPool.class)) {
                args.add(ClassPool.getDefault());
            } else {
                LOGGER.error("Unable to call method {} on plugin {}. Method parameter type {} is not recognized.",
                        pluginAnnotation.getMethod().getName(), plugin.getClass().getName(), type);
                return;
            }
        }
        try {
            pluginAnnotation.getMethod().invoke(plugin, args.toArray());

            // close CtClass if created from here
            if (ctClass != null) {
                ctClass.detach();
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("IllegalAccessException in method {} on plugin {}", e,
                    pluginAnnotation.getMethod().getName(), plugin.getClass().getName());
        } catch (InvocationTargetException e) {
            LOGGER.error("InvocationTargetException in method {} on plugin {}", e,
                    pluginAnnotation.getMethod().getName(), plugin.getClass().getName());
        }
    }


    /**
     * Creats javaassist CtClass for bytecode manipulation. Add default classloader.
     *
     * @param uri         uri
     * @param classLoader loader
     * @return created class
     * @throws NotFoundException
     */
    private CtClass createCtClass(URI uri, ClassLoader classLoader) throws NotFoundException, IOException {
        ClassPool cp = new ClassPool();
        cp.appendClassPath(new LoaderClassPath(classLoader));

        return cp.makeClass(uri.toURL().openStream());
    }
}
