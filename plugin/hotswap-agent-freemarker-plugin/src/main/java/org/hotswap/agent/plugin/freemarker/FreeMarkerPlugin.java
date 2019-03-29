package org.hotswap.agent.plugin.freemarker;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "FreeMarker",
        description = "Clear FreeMarker bean class introspection cache when class files are redefined.",
        testedVersions = { "2.3.28" },
        expectedVersions = { "2.3.20+"}
)
public class FreeMarkerPlugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(FreeMarkerPlugin.class);

    @Init
    private Scheduler scheduler;

    private final Command clearIntrospectionCache = new Command() {
        @Override
        public void executeCommand() {
            LOGGER.debug("Clearing FreeMarker BeanWrapper class introspection class.");
            try {
                Object config = ReflectionHelper.get(freeMarkerServlet, "config");
                Object  objectWrapper = ReflectionHelper.get(config, "objectWrapper");
                ReflectionHelper.invoke(objectWrapper, objectWrapper.getClass(), "clearClassIntrospecitonCache",new Class[] {});
                LOGGER.info("Cleared FreeMarker introspection cache");
            } catch(Exception e) {
                LOGGER.error("Error clearing FreeMarker introspection cache", e);
            }
        }
    };

    private Object freeMarkerServlet;

    @OnClassLoadEvent(classNameRegexp = "freemarker.ext.servlet.FreemarkerServlet")
    public static void init(ClassPool classPool, final CtClass ctClass) throws NotFoundException, CannotCompileException {

        String src = PluginManagerInvoker.buildInitializePlugin(FreeMarkerPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(FreeMarkerPlugin.class,
                "registerServlet", "this", "java.lang.Object");
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(src);

        LOGGER.debug("Patched freemarker.ext.servlet.FreemarkerServlet");
    }

    public void registerServlet(final Object freeMarkerServlet) {
        this.freeMarkerServlet = freeMarkerServlet;
        LOGGER.info("Plugin {} initialized for servlet {}", getClass(), this.freeMarkerServlet);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = { LoadEvent.REDEFINE })
    public void cacheReloader(CtClass ctClass) {
        scheduler.scheduleCommand(clearIntrospectionCache, 500);
    }
}