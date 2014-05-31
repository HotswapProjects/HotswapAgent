package org.hotswap.agent.plugin.tomcat;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;
import org.hotswap.agent.watch.Watcher;

import java.lang.reflect.Field;
import java.net.URLClassLoader;

/**
 * Catalina servlet container support.
 * <p/>
 * <p>Plugin</p><ul>
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

    /**
     * Init the plugin from start method.
     */
    @Transform(classNameRegexp = "org.apache.catalina.loader.WebappLoader")
    public static void patchWebappLoader(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        try {
            CtMethod startInternalMethod = ctClass.getDeclaredMethod("startInternal");
            // init the plugin
            String src = PluginManagerInvoker.buildInitializePlugin(TomcatPlugin.class, "classLoader");

            startInternalMethod.insertAfter(src);
        } catch (NotFoundException e) {
            LOGGER.warning("org.apache.catalina.loader.WebappLoader does not contain startInternal method. Tomcat plugin will be disabled.\n" +
                    "*** This is Ok, Tomcat plugin handles only special properties ***");
            return;
        }

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
        try {
            // force disable caching
            ctClass.getDeclaredMethod("isCachingAllowed").setBody("return false;");
        } catch (NotFoundException e) {
            LOGGER.debug("org.apache.catalina.core.StandardContext does not contain isCachingAllowed() method. Probably Ok.");
        }


        try {
            ctClass.getDeclaredMethod("stopInternal").insertBefore(
                PluginManagerInvoker.buildCallCloseClassLoader("getLoader().getClassLoader()")
            );
        } catch (NotFoundException e) {
            LOGGER.debug("org.apache.catalina.core.StandardContext does not contain stopInternal() method. Hotswap agent will not be able to free Tomcat plugin resources.");
        }
    }

}
