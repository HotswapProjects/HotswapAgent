package org.hotswap.agent.plugin.tomcat;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.ExtraPathResourceClassLoader;
import org.hotswap.agent.watch.Watcher;

import java.lang.reflect.Field;
import java.net.URLClassLoader;

/**
 * Catalina servlet container support.
 * <p/>
 * <p>Plugin</p><ul>
 * <li>Add extra classpath - prepend path to app classpath. This allows to use classes dir instead of JAR and
 * in consequence use hotswap on this files.</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Tomcat", description = "Catalina based servlet containers.",
        testedVersions = {"7.0.50"},
        expectedVersions = {""}
)
public class TomcatPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(TomcatPlugin.class);

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
    @Transform(classNameRegexp = "org.apache.catalina.loader.WebappClassLoader")
    public static void patchWebappClassLoader(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.addField(new CtField(classPool.get("java.lang.ClassLoader"), "__extraPathClassLoader", ctClass));

        CtMethod getResourceMethod = ctClass.getDeclaredMethod("findResource", new CtClass[]{classPool.get("java.lang.String")});
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
    @Transform(classNameRegexp = "org.apache.catalina.core.StandardContext")
    public static void patchStandardContext(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        // force disable caching
        ctClass.getDeclaredMethod("isCachingAllowed").setBody("return false;");

        ctClass.getDeclaredMethod("stopInternal").insertBefore(
                PluginManagerInvoker.buildCallCloseClassLoader("getLoader().getClassLoader()")
        );
    }

    @Transform(classNameRegexp = "org.apache.catalina.loader.WebappLoader")
    public static void patchWebappLoader(ClassPool pool, CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        CtMethod startInternalMethod = ctClass.getDeclaredMethod("startInternal");

        // init the plugin
        String src = PluginManagerInvoker.buildInitializePlugin(TomcatPlugin.class, "classLoader");

        // init extra path classloader
        src += PluginManagerInvoker.buildCallPluginMethod("classLoader", TomcatPlugin.class, "initExtraPathClassLoader");

        // register changeResourceClassLoader into WebAppClassLoader
        src += PluginManagerInvoker.buildCallPluginMethod("classLoader", TomcatPlugin.class,
                "registerExtraPathClassLoader",
                "classLoader", "java.lang.ClassLoader");

        startInternalMethod.insertAfter(src);
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
            LOGGER.error("Unable to register changed resource class loader on WebAppClassLoader {} class {} ", e, webAppClassLoader, webAppClassLoader.getClass());
        }
    }

}
