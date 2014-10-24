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
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
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
 * OSGI Equinox hotswap plugin. It watches class changes on extraClasspath and loads changed classes into appropriate equinox class loaders
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Equinox",
        description = "Supports hotswapping in OSGI/Equinox class loaders therefore it can be used for hotswap in Eclipse RCP plugin development. ",
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

    @Init
    ClassLoader appClassLoader;

    AutoHotswapPathEventListener listener;

    Set<ClassLoader> registeredEquinoxClassLoaders = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());

    // synchronize on this map to wait for previous processing
    final Map<Class<?>, byte[]> reloadMap = new HashMap<Class<?>, byte[]>();

    // command to do actual hotswap. Single command to merge possible multiple reload actions.
    Command hotswapCommand;

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
        LOGGER.debug("Init OsgiEquinoxPlugin at classLoader {}", appClassLoader);

        String extraClasspath = pluginConfiguration.getProperty("extraClasspath");

        if (extraClasspath != null) {
            URL extraClasspathURLs[] = convertToURL(extraClasspath);

            listener = new AutoHotswapPathEventListener(this);

            for (URL resource: extraClasspathURLs) {
                try {
                  URI uri = resource.toURI();
                  watcher.addEventListener(null, uri, listener);
                } catch (URISyntaxException e) {
                    LOGGER.error("Unable to watch path '{}' for changes.", e, resource);
                }
            }

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

    @OnClassLoadEvent(classNameRegexp = "org.eclipse.osgi.internal.loader.EquinoxClassLoader")
    public static void patchEquinoxClassLoaderClass(CtClass ctClass) throws CannotCompileException {
        String registerClassLoader = PluginManagerInvoker.buildCallPluginMethod(OsgiEquinoxPlugin.class, "registerEquinoxClassLoader", "this", "java.lang.Object");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerClassLoader);
        }
    }

    public void registerEquinoxClassLoader(Object equinoxClassLoader) {
        LOGGER.debug("OsgiEquinoxPlugin - registerEquinoxClassLoader : " + equinoxClassLoader.getClass().getName());
        registeredEquinoxClassLoaders.add((ClassLoader)equinoxClassLoader);
    }

    private static URL[] convertToURL(String resources) {
        List<URL> ret = new ArrayList<URL>();

        if (resources != null) {
            StringTokenizer tokenizer = new StringTokenizer(resources, ",;");
            while (tokenizer.hasMoreTokens()) {
                String name = tokenizer.nextToken().trim();
                try {
                    ret.add(resourceNameToURL(name));
                } catch (Exception e) {
                    LOGGER.error("Invalid configuration value: '{}' is not a valid URL or path and will be skipped.", name, e);
                }
            }
        }

        return ret.toArray(new URL[ret.size()]);
    }

    private static URL resourceNameToURL(String resource) throws Exception {
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

            File classFile = new File(event.getURI());
            CtClass ctClass = null;

            try {
                try {
                    ctClass = pool.makeClass(new FileInputStream(classFile));
                } catch (Exception e) {
                    LOGGER.warning("OsgiEquinox makeClass exception : {}",  e.getMessage());
                    return;
                }

                List<ClassLoader> targetClassLoaders = getTargetLoaders(ctClass);

                if (targetClassLoaders == null) {
                    OsgiEquinoxPlugin.LOGGER.trace("Class {} not loaded yet, no need for autoHotswap, skipped file {}", ctClass.getName(), event.getURI());
                    return;
                }

                LOGGER.debug("Class {} will be reloaded from URL {}", ctClass.getName(), event.getURI());

                loadClassToTargetClassLoaders(ctClass, targetClassLoaders);

                // search for a class to reload
            } finally {
                if (ctClass != null)
                    ctClass.detach();
            }

            equinoxPlugin.scheduler.scheduleCommand(equinoxPlugin.hotswapCommand, 100, Scheduler.DuplicateSheduleBehaviour.SKIP);
        }

        private void loadClassToTargetClassLoaders(CtClass ctClass, List<ClassLoader> targetClassLoaders) {
            for (ClassLoader classLoader: targetClassLoaders) {
              Class clazz;
              try {
                  clazz  = classLoader.loadClass(ctClass.getName());
                  synchronized (equinoxPlugin.reloadMap) {
                      equinoxPlugin.reloadMap.put(clazz, ctClass.toBytecode());
                  }
              } catch (ClassNotFoundException e) {
                  LOGGER.warning("OsgiEquinox tries to reload class {}, which is not known to application classLoader {}.",
                          ctClass.getName(), classLoader);
                  return;
              } catch (Exception e) {
                  LOGGER.warning("OsgiEquinox exception : {}",  e.getMessage());
                  return;
              }
            }

        }

        private List<ClassLoader> getTargetLoaders(CtClass ctClass) {
            List<ClassLoader> ret = null;
            synchronized (equinoxPlugin.registeredEquinoxClassLoaders) {
                for (ClassLoader classLoader: equinoxPlugin.registeredEquinoxClassLoaders) {
                    if (ClassLoaderHelper.isClassLoaded(classLoader, ctClass.getName())) {
                        if (ret == null)
                            ret = new ArrayList<>();
                        ret.add(classLoader);
                    }
                }
            }
            return ret;
        }
    }

}
