package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.watch.WatchEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to schedule after resource change.
 * <p/>
 * Equals is declared on all command params to group same change events to a single onWatchEvent. For event
 * only the URI is compared to group multiple event types.
 */
public class WatchEventCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WatchEventCommand.class);

    PluginAnnotation<Watch> pluginAnnotation;
    WatchEvent event;
    ClassLoader classLoader;

    public WatchEventCommand(PluginAnnotation<Watch> pluginAnnotation, WatchEvent event, ClassLoader classLoader) {
        this.pluginAnnotation = pluginAnnotation;
        this.event = event;
        this.classLoader = classLoader;
    }

    @Override
    public void executeCommand() {
        LOGGER.trace("Executing for pluginAnnotation={}, event={} at classloader {}", pluginAnnotation, event, classLoader);
        onWatchEvent(pluginAnnotation, event, classLoader);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WatchEventCommand that = (WatchEventCommand) o;

        if (classLoader != null ? !classLoader.equals(that.classLoader) : that.classLoader != null) return false;
        if (event != null ? !event.equals(that.event) : that.event != null) return false;
        if (pluginAnnotation != null ? !pluginAnnotation.equals(that.pluginAnnotation) : that.pluginAnnotation != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = pluginAnnotation != null ? pluginAnnotation.hashCode() : 0;
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (classLoader != null ? classLoader.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WatchEventCommand{" +
                "pluginAnnotation=" + pluginAnnotation +
                ", event=" + event +
                ", classLoader=" + classLoader +
                '}';
    }

    /**
     * Run plugin the method.
     */
    public void onWatchEvent(PluginAnnotation<Watch> pluginAnnotation, WatchEvent event, ClassLoader classLoader) {
        final Watch annot = pluginAnnotation.getAnnotation();
        Object plugin = pluginAnnotation.getPlugin();

        // regular files filter
        if (annot.onlyRegularFiles() && !event.isFile()) {
            LOGGER.trace("Skipping URI {} because it is not a regular file.", event.getURI());
            return;
        }

        // watch type filter
        if (!Arrays.asList(annot.watchEvents()).contains(event.getEventType())) {
            LOGGER.trace("Skipping URI {} because it is not a requested event.", event.getURI());
            return;
        }

        // resource nam filter regexp
        if (annot.filter() != null && annot.filter().length() > 0) {
            if (!event.getURI().toString().matches(annot.filter())) {
                LOGGER.trace("Skipping URI {} because it does not match filter.", event.getURI(), annot.filter());
                return;
            }
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
     * @throws org.hotswap.agent.javassist.NotFoundException
     */
    private CtClass createCtClass(URI uri, ClassLoader classLoader) throws NotFoundException, IOException {
        ClassPool cp = new ClassPool();
        cp.appendClassPath(new LoaderClassPath(classLoader));

        return cp.makeClass(new ByteArrayInputStream(IOUtils.toByteArray(uri)));
    }
}
