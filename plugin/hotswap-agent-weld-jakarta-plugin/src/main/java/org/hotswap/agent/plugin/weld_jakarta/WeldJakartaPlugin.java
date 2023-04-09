/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.weld_jakarta;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
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
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld_jakarta.command.BdaAgentRegistry;
import org.hotswap.agent.plugin.weld_jakarta.command.BeanClassRefreshCommand;
import org.hotswap.agent.plugin.weld_jakarta.transformer.AbstractClassBeanTransformer;
import org.hotswap.agent.plugin.weld_jakarta.transformer.BeanDeploymentArchiveTransformer;
import org.hotswap.agent.plugin.weld_jakarta.transformer.CdiContextsTransformer;
import org.hotswap.agent.plugin.weld_jakarta.transformer.ProxyFactoryTransformer;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;
import org.hotswap.agent.util.signature.ClassSignatureElement;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * WeldPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "WeldJakarta",
        description = "Weld Jakarta framework(http://weld.cdi-spec.org/). Reload, reinject bean, redefine proxy class after bean class definition/redefinition.",
        testedVersions = {"2.2.5-2.2.16, 2.3.x-3.1.x"},
        expectedVersions = {"All between 2.2.5-3.1.x"},
        supportClass = {BeanDeploymentArchiveTransformer.class, ProxyFactoryTransformer.class, AbstractClassBeanTransformer.class, CdiContextsTransformer.class})
public class WeldJakartaPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldJakartaPlugin.class);

    private static final String VETOED_ANNOTATION = "jakarta.enterprise.inject.Vetoed";
    private static final String DS_EXCLUDED_ANNOTATION = "org.apache.deltaspike.core.api.exclude.Exclude";

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

    private Map<Object, Object> registeredProxiedBeans = new WeakHashMap<>();

    private BeanReloadStrategy beanReloadStrategy;

    public void init() {
        if (!initialized) {
            LOGGER.info("Weld plugin initialized.");
            doInit();
        }
    }


    public void initInJBossAS() {
        if (!initialized) {
            LOGGER.info("Weld plugin initialized in JBossAS.");
            doInit();
        }
    }

    public void initInGlassFish() {
        if (!initialized) {
            LOGGER.info("Weld plugin initialized in GlassFish.");
            doInit();
        }
    }

    private void doInit() {
        initialized = true;
        beanReloadStrategy = setBeanReloadStrategy(pluginConfiguration.getProperty("weld.beanReloadStrategy"));
    }

    private BeanReloadStrategy setBeanReloadStrategy(String property) {
        BeanReloadStrategy ret = BeanReloadStrategy.NEVER;
        if (property != null && !property.isEmpty()) {
            try {
                ret = BeanReloadStrategy.valueOf(property);
            } catch (Exception e) {
                LOGGER.error("Unknown property 'weld.beanReloadStrategy' value: {} ", property);
            }
        }
        return ret;
    }

    /**
     * Register BeanDeploymentArchive's normalizedArchivePath to watcher. In case of new class, the class file is not known
     * to JVM hence no hotswap is called and therefore it must be handled by watcher.
     *
     * @param archivePath the archive path
     */
    public synchronized void registerBeanDeplArchivePath(final String archivePath) {
        URL resource = null;
        try {
            resource = resourceNameToURL(archivePath);
            URI uri = resource.toURI();
            if (!IOUtils.isDirectoryURL(uri.toURL())) {
                LOGGER.trace("Unable to watch for new files. Archive '{}' is not directory.", archivePath);
                return;
            } else {
                LOGGER.info("Registering archive path '{}'", archivePath);

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
                            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className) || isTestEnvironment) {
                                // refresh weld only for new classes
                                LOGGER.trace("Register reload command: {} ", className);
                                if (isBdaRegistered(appClassLoader, archivePath)) {
                                    // TODO : Create proxy factory
                                    scheduler.scheduleCommand(new BeanClassRefreshCommand(appClassLoader, archivePath, event), WAIT_ON_CREATE);
                                }
                            }
                        }
                    }
                });
            }
            LOGGER.info("Registered  watch for path '{}' for changes.", resource);
        } catch (URISyntaxException e) {
            LOGGER.error("Unable to watch path '{}' for changes.", e, archivePath);
        } catch (Exception e) {
            LOGGER.warning("registerBeanDeplArchivePath() exception : {}",  e.getMessage());
        }
    }

    private static boolean isBdaRegistered(ClassLoader classLoader, String archivePath) {
        if (archivePath != null) {
            try {
                return (boolean) ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader), "contains", new Class[] {String.class}, archivePath);
            } catch (ClassNotFoundException e) {
                LOGGER.error("isBdaRegistered() exception {}.", e.getMessage());
            }
        }
        return false;
    }

    public void registerProxyFactory(Object proxyFactory, Object bean, ClassLoader classLoader, Class<?> proxiedBeanType) {
        synchronized(registeredProxiedBeans) {
            if (!registeredProxiedBeans.containsKey(bean)) {
                LOGGER.debug("ProxyFactory for '{}' registered.", bean);
            }
            registeredProxiedBeans.put(bean, proxyFactory);
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
        if (AnnotationHelper.hasAnnotation(ctClass, VETOED_ANNOTATION)) {
            LOGGER.trace("Skipping @Vetoed class {}.", ctClass.getName());
            return;
        }

        if (original == null || isSyntheticCdiClass(ctClass.getName()) || isInnerNonPublicStaticClass(ctClass)) {
            if (original != null) {
                LOGGER.trace("Skipping synthetic or inner class {}.", original.getName());
            }
            return;
        }

        if (AnnotationHelper.hasAnnotation(ctClass, VETOED_ANNOTATION)) {
            LOGGER.trace("Skipping @Vetoed class {}.", ctClass.getName());
            return;
        }

        if (AnnotationHelper.hasAnnotation(ctClass, DS_EXCLUDED_ANNOTATION)) {
            LOGGER.trace("Skipping @Excluded class {}.", ctClass.getName());
            return;
        }

        try {
            String archivePath = getArchivePath(classLoader, ctClass, original.getName());
            if (isBdaRegistered(classLoader, archivePath)) {
                LOGGER.debug("Class '{}' redefined for archive {} ", original.getName(), archivePath);
                String oldSignatureForProxyCheck = WeldClassSignatureHelper.getSignatureForProxyClass(original);
                String oldSignatureByStrategy = WeldClassSignatureHelper.getSignatureByStrategy(beanReloadStrategy, original);
                String oldFullSignature = ClassSignatureComparerHelper.getJavaClassSignature(original, ClassSignatureElement.values());
                scheduler.scheduleCommand(new BeanClassRefreshCommand(classLoader, archivePath, registeredProxiedBeans,
                        original.getName(), oldFullSignature, oldSignatureForProxyCheck, oldSignatureByStrategy, beanReloadStrategy), WAIT_ON_REDEFINE);
            }
        } catch (Exception e) {
            LOGGER.error("classReload() exception {}.", e, e.getMessage());
        }
    }

    private String getArchivePath(ClassLoader classLoader, CtClass ctClass, String knownClassName) throws NotFoundException {
         try {
             return (String) ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader),
                     "getArchiveByClassName", new Class[] {String.class}, knownClassName);
         } catch (ClassNotFoundException e) {
             LOGGER.error("getArchivePath() exception {}.", e.getMessage());
         }

        String classFilePath = ctClass.getURL().getPath();
        String className = ctClass.getName().replace(".", "/");
        // archive path ends with '/', therefore we set end position before the '/' (-1)
        String archivePath = classFilePath.substring(0, classFilePath.indexOf(className) - 1);
        return (new File(archivePath)).toPath().toString();
    }

    public URL resourceNameToURL(String resource) throws Exception {
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

    // Return true if class is CDI synthetic class.
    // Weld proxies contains $Proxy$ and $$$
    // DeltaSpike's proxies contains "$$"
    private boolean isSyntheticCdiClass(String className) {
        return className.contains("$Proxy$") || className.contains("$$");
    }

    // Non static inner class is not allowed to be bean class
    private boolean isInnerNonPublicStaticClass(CtClass ctClass) {
        try {
            if (ctClass.isInnerClass() && (
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
