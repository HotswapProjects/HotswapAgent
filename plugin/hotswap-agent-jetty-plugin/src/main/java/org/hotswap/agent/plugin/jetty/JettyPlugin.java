package org.hotswap.agent.plugin.jetty;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Jetty servlet container support.
 * <p/>
 * <p>Plugin</p><ul>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Jetty", description = "Jetty plugin.",
        testedVersions = {"6.1.26"},
        expectedVersions = {"All versions supporting WebAppContext.getExtraClasspath"}
)
public class JettyPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JettyPlugin.class);


    /**
     * Before actual webapp initialization starts in ContextHandler.doStart(), do some enhancements:<ul>
     * <li>Initialize this plugin on the webapp classloader</li>
     * <li>Call plugin method initExtraPathResourceClassLoader add urls and to start watching changed resources</li>
     * <li>Call plugin method registerExtraPathClassLoader to inject enhanced resource loader to the webapp classloader.</li>
     * </ul>
     */
    @Transform(classNameRegexp = "org.mortbay.jetty.handler.ContextHandler")
    public static void patchContextHandler(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        try {
            // after application context initialized, but before processing started
            CtMethod doStart = ctClass.getDeclaredMethod("doStart");

            // init the plugin
            String src = PluginManagerInvoker.buildInitializePlugin(JettyPlugin.class, "getClassLoader()");

            doStart.insertBefore(src);
        } catch (NotFoundException e) {
            LOGGER.warning("org.mortbay.jetty.handler.ContextHandler does not contain doStart method. Jetty plugin will be disabled.\n" +
                    "*** This is Ok, Jetty plugin handles only special properties ***");
            return;
        }

        try {
            ctClass.getDeclaredMethod("doStop").insertBefore(
                    PluginManagerInvoker.buildCallCloseClassLoader("getClassLoader()")
            );
        } catch (NotFoundException e) {
            LOGGER.debug("org.mortbay.jetty.handler.ContextHandler does not contain doStop() method. Hotswap agent will not be able to free Jetty plugin resources.");
        }
    }
}
