package org.hotswap.agent.plugin.deltaspike.transformer;


import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Patch WindowContextImpl to handle external windowId
 *
 * @author Vladimir Dvorak
 */
public class DeltaspikeContextsTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaspikeContextsTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.impl.scope.window.WindowContextImpl")
    public static void patchWindowContext(CtClass ctClass) throws CannotCompileException, NotFoundException {

        CtField trackerFld = CtField.make("public java.util.Map " + HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD + "= new java.util.HashMap();", ctClass);
        ctClass.addField(trackerFld);

        CtMethod methInit = ctClass.getDeclaredMethod("init");
        methInit.insertBefore("windowIdHolder = new org.hotswap.agent.plugin.deltaspike.context.HaWindowIdHolder(windowIdHolder);");

        LOGGER.debug("org.apache.deltaspike.core.impl.scope.window.WindowContextImpl - patched by windowId handling.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.impl.scope.conversation.GroupedConversationContext")
    public static void patchGroupedConversationContext(CtClass ctClass) throws CannotCompileException, NotFoundException {

        CtField trackerFld = CtField.make("public java.util.Map " + HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD + "= new java.util.HashMap();", ctClass);
        ctClass.addField(trackerFld);

        ctClass.getDeclaredMethod("init")
            .insertAfter("org.hotswap.agent.plugin.deltaspike.context.GroupedConversationContextTracker.register(this.windowContext);");

        LOGGER.debug("org.apache.deltaspike.core.impl.scope.window.WindowContextImpl - patched by windowId handling.");
    }


    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder")
    public static void patchWindowBeanHolder(CtClass ctClass) throws CannotCompileException, NotFoundException {

        ctClass.getDeclaredMethod("init")
            .insertAfter("org.hotswap.agent.plugin.deltaspike.context.WindowContextsTracker.register();");

        ctClass.addMethod(CtNewMethod.make("public void " + HaCdiCommons.ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD + "(Object ctx) {" +
                    "org.hotswap.agent.plugin.deltaspike.context.WindowContextsTracker.attach(ctx);" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder - patched by window context tracker hooks.");
    }

}
