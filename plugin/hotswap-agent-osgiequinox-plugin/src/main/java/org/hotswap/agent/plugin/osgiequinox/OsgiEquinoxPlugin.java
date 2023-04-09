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
package org.hotswap.agent.plugin.osgiequinox;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * OSGI Equinox hotswap plugin. Watch class changes on extraClasspath and load modified classes into appropriate equinox class loader
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "OsgiEquinox",
        description = "Supports hotswapping in OSGI/Equinox class loader so it can be used for hotswap support in Eclipse RCP plugin development. ",
        testedVersions = {""},
        expectedVersions = {""})
public class OsgiEquinoxPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OsgiEquinoxPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    PluginManager pluginManager;

    @Init
    PluginConfiguration pluginConfiguration;

    @Init
    Watcher watcher;

    // synchronize on this map to wait for previous processing
    final Map<Class<?>, byte[]> reloadMap = new HashMap<>();

    private AutoHotswapPathEventListener listener;

    private Set<ClassLoader> registeredEquinoxClassLoaders = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());

    // command to do actual hotswap. Single command to merge possible multiple reload actions.
    private Command hotswapCommand;

    private String extraClasspath;

    private boolean isDebugMode;

    @OnClassLoadEvent(classNameRegexp = "org.eclipse.osgi.launch.Equinox")
    public static void patchEquinox(CtClass ctClass) throws CannotCompileException {
        String initializePlugin = PluginManagerInvoker.buildInitializePlugin(OsgiEquinoxPlugin.class);
        String initializeThis = PluginManagerInvoker.buildCallPluginMethod(OsgiEquinoxPlugin.class, "initOsgiEquinox");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(initializePlugin);
            constructor.insertAfter(initializeThis);
        }
    }

    public void initOsgiEquinox() {
        if (hotswapCommand != null)
            return;

        LOGGER.debug("Init OsgiEquinoxPlugin.");

        extraClasspath = pluginConfiguration.getProperty("extraClasspath");


        if (extraClasspath != null) {

            String debugMode = pluginConfiguration.getProperty("osgiEquinox.debugMode");
            isDebugMode = "true".equals(debugMode);

            if (!isDebugMode) {
                URL resource = null;
                try {
                    resource = resourceNameToURL(extraClasspath.trim());
                    URI uri = resource.toURI();
                    LOGGER.info("Initialize hotswap on URL {}.", uri);
                    listener = new AutoHotswapPathEventListener(this);
                    watcher.addEventListener(null, uri, listener);
                } catch (URISyntaxException e) {
                    LOGGER.error("Unable to watch path '{}' for changes.", e, resource);
                } catch (Exception e) {
                    LOGGER.warning("initOsgiEquinox() exception : {}",  e.getMessage());
                }

                if (resource != null) {
                    hotswapCommand = new Command() {
                        @Override
                        public void executeCommand() {
                            pluginManager.hotswap(reloadMap);
                        }

                        @Override
                        public String toString() {
                            return "pluginManager.hotswap(" + Arrays.toString(reloadMap.keySet().toArray()) + ")";
                        }
                    };
                }
            }

        }

    }

    @OnClassLoadEvent(classNameRegexp = "org.eclipse.osgi.internal.loader.EquinoxClassLoader")
    public static void patchEquinoxClassLoader(CtClass ctClass) throws CannotCompileException {
        String registerClassLoader = PluginManagerInvoker.buildCallPluginMethod(OsgiEquinoxPlugin.class, "registerEquinoxClassLoader", "this", "java.lang.Object");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerClassLoader);
        }
    }

    public void registerEquinoxClassLoader(Object equinoxClassLoader) {
        LOGGER.debug("RegisterEquinoxClassLoader : " + equinoxClassLoader.getClass().getName());
        registeredEquinoxClassLoaders.add((ClassLoader)equinoxClassLoader);
    }


    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(CtClass ctClass) {

        // Hotswap is realized by event listener in the RUNTIME mode
        if (!isDebugMode)
            return;

        try {
            URL url = ctClass.getURL();
            // Write content of class to extraClasspath, so classLoader.loadClass can load actual class
            ctClass.writeFile(extraClasspath);
            loadClassToTargetClassLoaders(ctClass, url.toURI(), false);
        } catch (Exception e) {
            LOGGER.warning("classReload() exception : {}",  e.getMessage());
        }
    }

    private void scheduleHotswapCommand() {
        scheduler.scheduleCommand(hotswapCommand, 100, Scheduler.DuplicateSheduleBehaviour.SKIP);
    }

    private boolean loadClassToTargetClassLoaders(CtClass ctClass, URI uri, boolean putToReloadMap) {
        List<ClassLoader> targetClassLoaders = getTargetLoaders(ctClass);

        if (targetClassLoaders == null) {
            LOGGER.trace("Class {} not loaded yet, no need for autoHotswap, skipped file {}", ctClass.getName());
            return false;
        }

        LOGGER.debug("Class {} will be reloaded from URL {}", ctClass.getName(), uri);

        ClassLoader classLoader = null;

        try {

            byte[] bytecode = ctClass.toBytecode();

            for (int i=0; i < targetClassLoaders.size(); i++) {
                classLoader = targetClassLoaders.get(i);

                Class clazz  = classLoader.loadClass(ctClass.getName());

                if (putToReloadMap) {
                    synchronized (reloadMap) {
                        reloadMap.put(clazz, bytecode);
                    }
                }
            }

        } catch (ClassNotFoundException e) {
            LOGGER.warning("OsgiEquinox tries to reload class {}, which is not known to Equinox classLoader {}.",
                ctClass.getName(), classLoader);
            return false;
        } catch (Exception e) {
            LOGGER.warning("loadClassToTargetClassLoaders() exception : {}",  e.getMessage());
            return false;
        }

        return true;
    }

    private List<ClassLoader> getTargetLoaders(CtClass ctClass) {
        List<ClassLoader> ret = null;
        synchronized (registeredEquinoxClassLoaders) {
            for (ClassLoader classLoader: registeredEquinoxClassLoaders) {
                if (ClassLoaderHelper.isClassLoaded(classLoader, ctClass.getName())) {
                    if (ret == null)
                        ret = new ArrayList<ClassLoader>();
                    ret.add(classLoader);
                }
            }
        }
        return ret;
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

    // AutoHotswapPathEventListener
    private static class AutoHotswapPathEventListener implements WatchEventListener {
        private OsgiEquinoxPlugin equinoxPlugin;

        public AutoHotswapPathEventListener(OsgiEquinoxPlugin equinoxPlugin) {
            this.equinoxPlugin = equinoxPlugin;
        }

        @Override
        public void onEvent(WatchFileEvent event) {
            ClassPool pool = ClassPool.getDefault();

            if (!event.getURI().getPath().endsWith(".class")) {
                return;
            }

            URI fileURI = event.getURI();

            File classFile = new File(fileURI);
            CtClass ctClass = null;

            boolean doHotswap = false;
            try {
                ctClass = pool.makeClass(new FileInputStream(classFile));
                doHotswap = equinoxPlugin.loadClassToTargetClassLoaders(ctClass, fileURI, true);
            } catch (Exception e) {
                LOGGER.warning("MakeClass exception : {}",  e.getMessage());
            } finally {
                if (ctClass != null) {
                    ctClass.detach();
                }
            }

            if (doHotswap)
                equinoxPlugin.scheduleHotswapCommand();
        }

    }
}
