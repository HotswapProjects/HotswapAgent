package org.hotswap.agent.plugin.owb;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * WebBeanContextTransformer - extend WebContextsService by WebContextsTracker
 *
 * @author Vladimir Dvorak
 */
public class WebBeansContextsServiceTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WebBeansContextsServiceTransformer.class);

    public static final String CONTEXT_TRACKER_FLD_NAME = "__contextTracker";

    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.web.context.WebContextsService")
    public static void transform(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtField contextTrackerFld = CtField.make("public org.hotswap.agent.plugin.owb.command.WebContextsTracker " + CONTEXT_TRACKER_FLD_NAME + ";", ctClass);
        ctClass.addField(contextTrackerFld);

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("org.apache.webbeans.config.WebBeansContext")
        };

        CtConstructor declaredConstructor = ctClass.getDeclaredConstructor(constructorParams);

        declaredConstructor.insertAfter(
                "{ " +
                        "this." + CONTEXT_TRACKER_FLD_NAME + " = new org.hotswap.agent.plugin.owb.command.WebContextsTracker();" +
                        "this.requestContexts = this." + CONTEXT_TRACKER_FLD_NAME + ".requestContexts; " +
                        "this.sessionContexts = this." + CONTEXT_TRACKER_FLD_NAME + ".sessionContexts; " +
                        "this.conversationContexts = this." + CONTEXT_TRACKER_FLD_NAME + ".conversationContexts;" +
                "}"
        );

        LOGGER.debug("Class '{}' patched with .", ctClass.getName());
    }
}
