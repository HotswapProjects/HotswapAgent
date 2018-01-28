package org.hotswap.agent.plugin.owb.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * WebBeanContextTransformer - extend WebContextsService by SessionContextsTracker
 *
 * @author Vladimir Dvorak
 */
public class WebBeansContextsServiceTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WebBeansContextsServiceTransformer.class);

    public static final String SESSION_CONTEXTS_TRACKER_FIELD = "$$ha$sessionContextsTracker";

    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.web.context.WebContextsService")
    public static void transform(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtField trackerFld =
                CtField.make("public org.hotswap.agent.plugin.owb.command.SessionContextsTracker " + SESSION_CONTEXTS_TRACKER_FIELD + ";", ctClass);

        ctClass.addField(trackerFld);

        for (CtConstructor constructor: ctClass.getConstructors()) {
            constructor.insertAfter(
                "this." + SESSION_CONTEXTS_TRACKER_FIELD + " = new org.hotswap.agent.plugin.owb.command.SessionContextsTracker();" +
                "this." + SESSION_CONTEXTS_TRACKER_FIELD + ".sessionContexts = this.sessionContexts; "
            );
        }

        CtMethod initSessionContextMethod = ctClass.getDeclaredMethod("initSessionContext");
        initSessionContextMethod.insertAfter("this." + SESSION_CONTEXTS_TRACKER_FIELD + ".addSessionContext();");

        CtMethod destroySessionContextMethod = ctClass.getDeclaredMethod("destroySessionContext");
        destroySessionContextMethod.insertBefore("this." + SESSION_CONTEXTS_TRACKER_FIELD + ".removeSessionContext();");

        LOGGER.info("Class '{}' patched with .", ctClass.getName());
    }
}
