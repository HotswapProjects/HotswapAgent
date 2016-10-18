package org.hotswap.agent.plugin.log4j2;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

import java.net.URI;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Log4j2 configuration file reload.
 *
 * @author Lukasz Warzecha
 */
@Plugin(name = "Log4j2", description = "Log4j2 configuration reload.",
        testedVersions = { "2.5" })
public class Log4j2Plugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(Log4j2Plugin.class);

    @Init
    Watcher watcher;

    @Init
    ClassLoader appClassLoader;

    // ensure uri registered only once
    Set<URI> registeredURIs = new HashSet<>();

    /**
     * Callback method from org.apache.logging.log4j.core.LoggerContext.
     * 
     * @param configURI configuration file uri
     */
    public void init(final URI configURI) {
        if (configURI != null) {
            final URI uri = Paths.get(configURI).getParent().toUri();
            try {
                // skip double registration on reload
                if (registeredURIs.contains(uri)) {
                    return;
                }

                LOGGER.debug("Watching '{}' URI for Log4j2 configuration changes.", uri);
                registeredURIs.add(uri);
                watcher.addEventListener(appClassLoader, uri, new WatchEventListener() {

                    @Override
                    public void onEvent(WatchFileEvent event) {
                        if (event.getEventType() != FileEvent.DELETE && event.getURI().equals(configURI)) {
                            reload(configURI);
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Exception initializing Log4j2 on uri {}.", e, uri);
            }
        }
    }

    /**
     * Do the reload using Log4j2 configurator.
     *
     * @param uri the configuration file uri
     */
    protected void reload(URI uri) {

        try {
            IOUtils.toByteArray(uri);
        } catch (Exception e) {
            LOGGER.warning("Unable to open Log4j2 configuration file {}, is it deleted?", uri);
            return;
        }

        try {
            ClassLoader classLoader = this.getClass().getClassLoader();

            Class<?> logManagerClass = classLoader.loadClass("org.apache.logging.log4j.LogManager");
            Class<?> contextClass = classLoader.loadClass("org.apache.logging.log4j.core.LoggerContext");

            Object context = logManagerClass.getDeclaredMethod("getContext", Boolean.TYPE).invoke(logManagerClass,
                    true);

            // resetting configLocation forces reconfiguration
            contextClass.getDeclaredMethod("setConfigLocation", URI.class).invoke(context, uri);

            LOGGER.reload("Log4j2 configuration reloaded from uri '{}'.", uri);
        } catch (Exception e) {
            LOGGER.error("Unable to reload {} with Log4j2", e, uri);
        }
    }

    /**
     * Transform configurator class to register log4j2 config URI.
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.logging.log4j.core.LoggerContext")
    public static void registerConfigurator(ClassPool classPool, CtClass ctClass) throws NotFoundException,
            CannotCompileException {
        CtMethod m = ctClass.getDeclaredMethod("reconfigure", new CtClass[] { classPool.get("java.net.URI") });

        m.insertAfter(PluginManagerInvoker.buildInitializePlugin(Log4j2Plugin.class));
        m.insertAfter(PluginManagerInvoker.buildCallPluginMethod(Log4j2Plugin.class, "init",
                "configURI", "java.net.URI"));
    }

}
