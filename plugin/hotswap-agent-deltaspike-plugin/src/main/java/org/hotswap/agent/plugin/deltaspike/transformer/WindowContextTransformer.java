package org.hotswap.agent.plugin.deltaspike.transformer;


import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Patch WindowContextImpl to handle external windowId
 */
public class WindowContextTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WindowContextTransformer.class);

    public static final String ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD = "$$ha$attachCustomContextTracker";

//    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.api.scope.WindowScoped")
//    public static void patchWindowScoped(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
//
//        ClassFile cfile = ctClass.getClassFile();
//        ConstPool cpool = cfile.getConstPool();
//
//        AnnotationsAttribute attr = new AnnotationsAttribute(cpool, AnnotationsAttribute.visibleTag);
//        Annotation a = new Annotation("org.hotswap.agent.plugin.deltaspike.transformer.HaTrackedSessionScope", cpool);
//        attr.addAnnotation(a);
//        cfile.addAttribute(attr);
//
//        LOGGER.debug("org.apache.deltaspike.core.api.scop.WindowScoped - patched by HaTrackedSessionScope annotation.");
//    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.impl.scope.window.WindowContextImpl")
    public static void patchWindowContext(CtClass ctClass) throws CannotCompileException, NotFoundException {

        CtMethod methInit = ctClass.getDeclaredMethod("init");
        methInit.insertBefore("windowIdHolder = new org.hotswap.agent.plugin.deltaspike.context.HaWindowIdHolder(windowIdHolder);");

        LOGGER.debug("org.apache.deltaspike.core.impl.scope.window.WindowContextImpl - patched by windowId handling.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder")
    public static void patchWindowBeanHolder(CtClass ctClass) throws CannotCompileException, NotFoundException {

        ctClass.getDeclaredMethod("init")
            .insertAfter("org.hotswap.agent.plugin.deltaspike.context.WindowContextsTracker.register();");

        ctClass.addMethod(CtNewMethod.make("public void " + ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD + "(Object ctx) {" +
                    "org.hotswap.agent.plugin.deltaspike.context.WindowContextsTracker.attach(ctx);" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder - patched by window context tracker hooks.");
    }

}
