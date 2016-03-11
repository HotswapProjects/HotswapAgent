package org.hotswap.agent.plugin.weld;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.command.BdaAgentRegistry;
import org.hotswap.agent.plugin.weld.command.BeanClassRefreshCommand;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * WeldPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Weld",
        description = "Weld framework(http://weld.cdi-spec.org/). Reload, reinject bean, redefine proxy class after bean class definition/redefinition.",
        testedVersions = {"2.2.6"},
        expectedVersions = {"All between 2.0 - 2.2"},
        supportClass = {BeanDeploymentArchiveTransformer.class, ProxyFactoryTransformer.class})
public class WeldPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);

    static boolean IS_TEST_ENVIRONMENT = Boolean.FALSE;

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

    boolean inJbossAS = false;

    boolean initialized = false;

    private Map<Object, Object> registeredProxiedBeans = new WeakHashMap<Object, Object>();

    public void init() {
        if (!initialized) {
            LOGGER.info("CDI/Weld plugin initialized.");
            initialized = true;
        }
    }

    public void initInJBossAS() {
        if (!initialized) {
            LOGGER.info("CDI/Weld plugin initialized in JBossAS.");
            inJbossAS = true;
            initialized = true;
        }
    }

    /**
     * Register BeanDeploymentArchive by bdaId to watcher. In case of new class the class file is not known
     * to JVM hence no hotswap is called and therefore it must be handled by watcher.
     *
     * @param bdaId the BeanDeploymentArchive ID
     */
    public synchronized void registerBeanDeplArchivePath(final String archivePath) {
        LOGGER.info("Registering archive path {}", archivePath);

        URL resource = null;
        try {
            resource = resourceNameToURL(archivePath);
            URI uri = resource.toURI();
            if (!IOUtils.isFileURL(uri.toURL())) {
                LOGGER.debug("Weld - unable to watch files on URL '{}' for changes (JAR file?)", archivePath);
                return;
            } else {
                watcher.addEventListener(appClassLoader, uri, new WatchEventListener() {
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
                            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className) || IS_TEST_ENVIRONMENT) {
                                // refresh weld only for new classes
                                LOGGER.trace("register reload command: {} ", className);
                                if (isBdaRegistered(appClassLoader, archivePath)) {
                                    // TODO : Create proxy factory
                                    scheduler.scheduleCommand(new BeanClassRefreshCommand(appClassLoader, archivePath, event), WAIT_ON_CREATE);
                                }
                            }
                        }
                    }
                });
            }
        } catch (URISyntaxException e) {
            LOGGER.error("Unable to watch path '{}' for changes.", e, resource);
        } catch (Exception e) {
            LOGGER.warning("registerBeanDeplArchivePath() exception : {}",  e.getMessage());
        }
    }

    private URL resourceNameToURL(String resource) throws Exception {
        try {
            // Try to format as a URL?
            return new URL(resource);
        } catch (MalformedURLException e) {
            // try to locate a file
            if (resource.startsWith("./"))
                resource = resource.substring(2);
            File file = new File(resource).getCanonicalFile();
            return file.toURI().toURL();
        }
    }

    private static boolean isBdaRegistered(ClassLoader classLoader, String archivePath) {
        if (archivePath != null) {
            try {
                return (boolean) ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader),
                        "contains", new Class[] {String.class}, archivePath);
            } catch (ClassNotFoundException e) {
                LOGGER.error("isBdaRegistered() exception {}.", e.getMessage());
            }
        }
        return false;
    }

    public void registerProxyFactory(Object proxyFactory, Object bean, ClassLoader classLoader) {
        synchronized(registeredProxiedBeans) {
            registeredProxiedBeans.put(bean, proxyFactory);
        }
        LOGGER.debug("Registering ProxyFactory : " + proxyFactory.getClass().getName());
    }

    /**
     * If bda archive is defined for given class than new BeanClassRefreshCommand is created
     *
     * @param classLoader
     * @param ctClass
     * @param original
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(ClassLoader classLoader, CtClass ctClass, Class original) {
        if (!isSyntheticCdiClass(ctClass.getName()) && original != null) {
            try {
                String archivePath = getArchivePath(ctClass);
                if (isBdaRegistered(classLoader, archivePath)) {
                    String oldSignature = ProxyClassSignatureHelper.getJavaClassSignature(original);
                    scheduler.scheduleCommand(new BeanClassRefreshCommand(classLoader, archivePath,
                            registeredProxiedBeans, original.getName(), oldSignature), WAIT_ON_REDEFINE);
                }
            } catch (Exception e) {
                LOGGER.error("classReload() exception {}.", e.getMessage());
            }
        }
    }

    private String getArchivePath(CtClass ctClass) throws NotFoundException {
        String classFilePath = ctClass.getURL().getPath();
        String className = ctClass.getName().replace(".", "/");
        // archive path ends with '/' therefore we set end position before the '/' (-1)
        String archivePath = classFilePath.substring(0, classFilePath.indexOf(className) - 1);
        return (new File(archivePath)).toPath().toString();
    }

    // Return true if class is CDI synthetic class.
    // Weld proxies contains $Proxy$ and $$$
    // DeltaSpike's proxies contains "$$"
    private boolean isSyntheticCdiClass(String className) {
        return className.contains("$Proxy$") || className.contains("$$");
    }

}
