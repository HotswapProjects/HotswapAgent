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
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hotswapper.HotswapperCommand;
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
@Plugin(name = "OsgiEquinox",
        description = "Supports hotswapping in OSGI/Equinox class loaders therefore it can be used for hotswap in Eclipse RCP plugin development. ",
        testedVersions = {""},
        expectedVersions = {""})
public class OsgiEquinoxPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OsgiEquinoxPlugin.class);

    @Init
    ClassLoader appClassLoader;

    @Init
    Scheduler scheduler;

    @Init
    PluginManager pluginManager;

    @Init
    PluginConfiguration pluginConfiguration;

    @Init
    Watcher watcher;

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

    	if (hotswapCommand != null)
    		return;

        LOGGER.debug("Init OsgiEquinoxPlugin.");

        String extraClasspath = pluginConfiguration.getProperty("extraClasspath");

        if (extraClasspath != null) {

            URL extraClasspathURLs[] = convertToURL(extraClasspath);

            listener = new AutoHotswapPathEventListener(this);

            for (URL resource: extraClasspathURLs) {
                try {
                  URI uri = resource.toURI();
                  LOGGER.info("Initialize hotswap on URL {}.", uri);
                  watcher.addEventListener(null, uri, listener);
                } catch (URISyntaxException e) {
                    LOGGER.error("Unable to watch path '{}' for changes.", e, resource);
                }
            }

            final String port = pluginConfiguration.getProperty("autoHotswap.port");

            if (port == null || port.isEmpty()) {
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
            else {
                hotswapCommand = new ReflectionCommand(this, HotswapperCommand.class.getName(), "hotswap", appClassLoader,
                        port, reloadMap);
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
    public void classReload(CtClass ctClass, Class original) {
    	try {
            URL url = ctClass.getURL();
            if (loadClassToTargetClassLoaders(ctClass, url.toURI())) {
                scheduleHotswapCommand();
            }
		} catch (Exception e) {
            LOGGER.warning("Exception : {}",  e.getMessage());
		}
    }

	private void scheduleHotswapCommand() {
        scheduler.scheduleCommand(hotswapCommand, 100, Scheduler.DuplicateSheduleBehaviour.SKIP);
	}

    private List<ClassLoader> getTargetLoaders(CtClass ctClass) {
        List<ClassLoader> ret = null;
        synchronized (registeredEquinoxClassLoaders) {
            for (ClassLoader classLoader: registeredEquinoxClassLoaders) {
                if (ClassLoaderHelper.isClassLoaded(classLoader, ctClass.getName())) {
                    if (ret == null)
                        ret = new ArrayList<>();
                    ret.add(classLoader);
                }
            }
        }
        return ret;
    }

    private boolean loadClassToTargetClassLoaders(CtClass ctClass, URI uri) {
        List<ClassLoader> targetClassLoaders = getTargetLoaders(ctClass);

        if (targetClassLoaders == null) {
            LOGGER.trace("Class {} not loaded yet, no need for autoHotswap, skipped file {}", ctClass.getName());
            return false;
        }

        LOGGER.debug("Class {} will be reloaded from URL {}", ctClass.getName(), uri);

        for (ClassLoader classLoader: targetClassLoaders) {
            try {
                Class clazz  = classLoader.loadClass(ctClass.getName());

                synchronized (reloadMap) {
                    reloadMap.put(clazz, ctClass.toBytecode());
                }

            } catch (ClassNotFoundException e) {
                LOGGER.warning("OsgiEquinox tries to reload class {}, which is not known to Equinox classLoader {}.",
                    ctClass.getName(), classLoader);
                return false;
            } catch (Exception e) {
                LOGGER.warning("Exception : {}",  e.getMessage());
                return false;
            }
        }

        return true;
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

            URI fileURI = event.getURI();

            File classFile = new File(fileURI);
            CtClass ctClass = null;

            boolean doHotswap = false;
            try {
                ctClass = pool.makeClass(new FileInputStream(classFile));
                doHotswap = equinoxPlugin.loadClassToTargetClassLoaders(ctClass, fileURI);
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
