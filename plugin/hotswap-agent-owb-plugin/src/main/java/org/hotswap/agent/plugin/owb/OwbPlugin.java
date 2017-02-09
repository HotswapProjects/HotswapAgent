package org.hotswap.agent.plugin.owb;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.owb.command.BeanClassRefreshCommand;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * OwbPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Owb",
        description = "Open Web Beans framework(http://openwebbeans.apache.org/). Reload, reinject bean, redefine proxy class after bean class definition/redefinition.",
        testedVersions = {"1.7.0"},
        expectedVersions = {"All between 1.7.0-1.7.0"},
        supportClass = { BeanArchiveTransformer.class, CdiContextsTransformer.class, WebBeansContextTransformer.class, WebBeansContextsServiceTransformer.class})
public class OwbPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OwbPlugin.class);

    /** True for UnitTests */
    static boolean isTestEnvironment = false;

    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait for this this timeout(milliseconds) after class file event before ClassPathBeanRefreshCommand
     */
    private static final int WAIT_ON_CREATE = 500;
    private static final int WAIT_ON_REDEFINE = 200;

    @Init
    Watcher watcher;

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @Init
    PluginConfiguration pluginConfiguration;

    boolean initialized = false;

    BeanReloadStrategy beanReloadStrategy;

    Set<URL> registeredArchives = new HashSet<>();

    /**
     * Plugin initialization, called from archive registration,
     */
    public void init() {
        if (!initialized) {
            LOGGER.info("CDI/Owb plugin initialized.");
            initialized = true;
            beanReloadStrategy = setBeanReloadStrategy(pluginConfiguration.getProperty("owb.beanReloadStrategy"));
        }
    }

    private BeanReloadStrategy setBeanReloadStrategy(String property) {
        BeanReloadStrategy ret = BeanReloadStrategy.NEVER;
        if (property != null && !property.isEmpty()) {
            try {
                ret = BeanReloadStrategy.valueOf(property);
            } catch (Exception e) {
                LOGGER.error("Unknown property 'owb.beanReloadStrategy' value: {} ", property);
            }
        }
        return ret;
    }

    /**
     * Register Archive's direcotry to watcher. In case of new class the new class file is not known
     * to JVM hence no redefinition callback is called and then it must be handled by watcher.
     *
     * @param archivePath the archive path
     */
    public synchronized void registerArchiveDir(File dir) {
        URL resource = null;
        try {
            if (!dir.isDirectory()) {
                LOGGER.trace("Owb - unable to watch files on '{}' for changes. Not a directory.", dir.getName());
                return;
            }

            URL archiveURL = dir.toURI().toURL();
            final String archivePath = dir.getPath();

            if (registeredArchives.contains(archiveURL)) {
                LOGGER.trace("Owb - archive path '{}' already registered.", archivePath);
                return;
            }

            registeredArchives.add(archiveURL);

            LOGGER.info("Registering archive path {}", archivePath);

            watcher.addEventListener(appClassLoader, archiveURL, new WatchEventListener() {
                @Override
                public void onEvent(WatchFileEvent event) {
                    if (event.isFile() && event.getURI().toString().endsWith(".class")) {
                        // check that the class is not loaded by the classloader yet (avoid duplicate reload)
                        String className;
                        try {
                            className = IOUtils.urlToClassName(event.getURI());
                        } catch (IOException e) {
                            LOGGER.trace("Watch event on resource '{}' skipped, probably Ok because of delete/create event sequence (compilation not finished yet).",
                                    e, event.getURI());
                            return;
                        }
                        if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className) || isTestEnvironment) {
                            // refresh weld only for new classes
                            LOGGER.trace("register reload command: {} ", className);
                            scheduler.scheduleCommand(new BeanClassRefreshCommand(appClassLoader, archivePath, event), WAIT_ON_CREATE);
                        }
                    }
                }
            });
            LOGGER.info("Registered  watch for path '{}' for changes.", resource);
        } catch (Exception e) {
            LOGGER.warning("registerBeanArchivePath() exception : {}",  e.getMessage());
        }
    }

    /**
     * If bda archive is defined for given class than new BeanClassRefreshCommand is created
     *
     * @param classLoader
     * @param ctClass
     * @param original
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(ClassLoader classLoader, CtClass ctClass, Class<?> original) {
        if (original != null && !isSyntheticCdiClass(ctClass.getName()) && !isInnerNonPublicStaticClass(ctClass)) {
            try {
                String oldSignatureForProxyCheck = OwbClassSignatureHelper.getSignatureForProxyClass(original);
                String oldSignatureByStrategy = OwbClassSignatureHelper.getSignatureByStrategy(beanReloadStrategy, original);
                scheduler.scheduleCommand(new BeanClassRefreshCommand(classLoader, original.getName(),
                        oldSignatureForProxyCheck, oldSignatureByStrategy, beanReloadStrategy), WAIT_ON_REDEFINE);
            } catch (Exception e) {
                LOGGER.error("classReload() exception {}.", e, e.getMessage());
            }
        }
    }


    // Return true if class is CDI synthetic class.
    // Owb proxies contains $Proxy$ and $$$
    // DeltaSpike's proxies contains "$$"
    private boolean isSyntheticCdiClass(String className) {
        return className.contains("$Proxy$") || className.contains("$$");
    }

    // Non static inner class is not allowed to be bean class
    private boolean isInnerNonPublicStaticClass(CtClass ctClass) {
        try {
            CtClass declaringClass = ctClass.getDeclaringClass();
            if (declaringClass != null && (
                    (ctClass.getModifiers() & Modifier.STATIC) == 0 ||
                    (ctClass.getModifiers() & Modifier.PUBLIC) == 0)) {
                return true;
            }
        } catch (NotFoundException e) {
            // swallow exception
        }
        return false;
    }

}
