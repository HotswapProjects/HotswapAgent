package org.hotswap.agent.plugin.jetty;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.ExtraPathResourceClassLoader;
import org.hotswap.agent.watch.Watcher;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Jetty servlet container support.
 * <p/>
 * <p>Plugin</p><ul>
 * <li>Add extra classpath - prepend path to app classpath. This allows to use classes dir instead of JAR and
 * in consequence use hotswap on this files.</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Jetty", description = "Jetty plugin - add extra classpath to the app classloader.",
        testedVersions = {"6.1.26"},
        expectedVersions = {"All versions supporting WebAppContext.getExtraClasspath"}
)
public class JettyPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JettyPlugin.class);

    @Init
    PluginConfiguration pluginConfiguration;
    @Init
    Watcher watcher;
    @Init
    ClassLoader appClassLoader;

    /**
     * ClassLoader to prefer resources from source directory instead of target directory. Use
     * changed resource only AFTER the resource is changed.
     */
    ExtraPathResourceClassLoader extraPathResourceClassLoader = new ExtraPathResourceClassLoader();

    /**
     * Declare new field __extraPathClassLoader and initialize it to ExtraPathResourceClassLoader instance in constructor.
     * Delegate getResource() and getResources() to this classloader first.
     * <p/>
     * Use plugin method registerChangedResourceClassLoader to share extraPathResourceClassLoader instance between
     * plugin classloader and the newly created app classloader.
     */
    @Transform(classNameRegexp = "org.mortbay.jetty.webapp.WebAppClassLoader")
    public static void patchWebAppClassLoader(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.addField(new CtField(classPool.get("java.lang.ClassLoader"), "__extraPathClassLoader", ctClass));

        CtMethod getResourceMethod = ctClass.getDeclaredMethod("getResource", new CtClass[]{classPool.get("java.lang.String")});
        getResourceMethod.insertBefore("{ " +
                "if (__extraPathClassLoader != null &&__extraPathClassLoader.getResource($1) != null) " +
                "   return __extraPathClassLoader.getResource($1); }");

        ctClass.addMethod(CtMethod.make(
                "public java.util.Enumeration getResources(java.lang.String name) {" +
                        "if (__extraPathClassLoader == null) return super.getResources(name);" +
                        "java.util.Enumeration e = __extraPathClassLoader.getResources(name);" +
                        "if (e.hasMoreElements()) return e;" +
                        "   else return super.getResources(name);" +
                        "}", ctClass));
    }

    /**
     * Before actual webapp initialization starts in ContextHandler.doStart(), do some enhancements:<ul>
     * <li>Initialize this plugin on the webapp classloader</li>
     * <li>Call plugin method initExtraPathClassLoader add urls and to start watching changed resources</li>
     * <li>Call plugin method registerExtraPathClassLoader to inject enhanced resource loader to the webapp classloader.</li>
     * </ul>
     */
    @Transform(classNameRegexp = "org.mortbay.jetty.handler.ContextHandler")
    public static void patchContextHandler(ClassLoader classLoader, CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        // after application context initialized, but before processing started
        CtMethod doStart = ctClass.getDeclaredMethod("doStart");

        // init the plugin
        String src = PluginManagerInvoker.buildInitializePlugin(JettyPlugin.class, "getClassLoader()");

        // mapPreviousState changed resources watcher
        src += PluginManagerInvoker.buildCallPluginMethod("getClassLoader()", JettyPlugin.class, "initExtraPathClassLoader");

        // register changeResourceClassLoader into WebAppClassLoader
        src += PluginManagerInvoker.buildCallPluginMethod("getClassLoader()", JettyPlugin.class,
                "registerExtraPathClassLoader",
                "getClassLoader()", "java.lang.ClassLoader");

        doStart.insertBefore(src);
    }


    /**
     * Adds extrapath to the classloader and intis the resource classlaode plugin
     */
    public void initExtraPathClassLoader() {
        if (!(appClassLoader instanceof URLClassLoader)) {
            LOGGER.warning("Unable to add extraPath to application classloader. Classloader '{}' is of type '{}'," +
                    "but only URLClassLoader is supported", appClassLoader, appClassLoader.getClass());
            return;
        }

        extraPathResourceClassLoader.init(pluginConfiguration.getWatchResources(), watcher);
    }

    /**
     * Set changed resource class loader on WebAppClassLoader via reflection - different classloader.
     */
    public void registerExtraPathClassLoader(ClassLoader webAppClassLoader) {
        try {
            Field f = webAppClassLoader.getClass().getDeclaredField("__extraPathClassLoader");
            f.setAccessible(true);
            f.set(webAppClassLoader, extraPathResourceClassLoader);
        } catch (Exception e) {
            LOGGER.error("Unable to register changed resource class loader on WebAppClassLoader {}", e, webAppClassLoader);
        }
    }

}
