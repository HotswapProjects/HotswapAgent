package org.hotswap.agent.plugin.owb;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into org.apache.webbeans.corespi.scanner.xbean.CdiArchivee constructors to initialize OwbPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeanArchiveTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanArchiveTransformer.class);

    /**
     * Basic CdiArchive transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.xbean.finder.archive.FileArchive")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(OwbPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(OwbPlugin.class, "init"));
        src.append("org.hotswap.agent.plugin.owb.command.BeanArchiveAgent.registerArchive(loader, this);");
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class '{}' patched with CdiArchive registration.", clazz.getName());
    }

}
